package org.openfactor.app

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

@Composable fun ExerciseLibrary(exercises:List<Exercise>,favorites:Set<String>,onFavorite:(String)->Unit,onDetail:(String)->Unit,onPick:((String)->Unit)?=null,onBack:(()->Unit)?=null,onCustom:()->Unit={},picking:Boolean=false) {
    var query by rememberSaveable {mutableStateOf("")}
    var muscle by rememberSaveable {mutableStateOf("All muscles")}
    var equipment by rememberSaveable {mutableStateOf("All equipment")}
    var onlyFavorites by rememberSaveable {mutableStateOf(false)}
    var filter by remember {mutableStateOf(false)}
    val filtered=remember(exercises,query,muscle,equipment,onlyFavorites,favorites) {exercises.filter {e -> (query.isBlank() || e.name.contains(query,true)) && (muscle=="All muscles" || muscle==e.muscle) && (equipment=="All equipment" || equipment==e.equipment) && (!onlyFavorites || e.id in favorites)}.sortedBy {it.name}}
    Column(Modifier.fillMaxSize()) {
        PageHeading(if(picking)"Add Exercise" else "Exercises",onBack,actions={RoundAction(Icons.Rounded.Add,"Create custom exercise",onCustom)})
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=10.dp),placeholder={Text("Search exercises")},leadingIcon={Icon(Icons.Rounded.Search,null)},trailingIcon={if(query.isNotEmpty())IconButton({query=""}){Icon(Icons.Rounded.Close,"Clear search")}},singleLine=true,shape=RoundedCornerShape(50))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(filter || muscle!="All muscles" || equipment!="All equipment",{filter=!filter},label={Text("Filters")},leadingIcon={Icon(Icons.Rounded.Tune,null,Modifier.size(18.dp))})
            FilterChip(onlyFavorites,{onlyFavorites=!onlyFavorites},label={Text("Favorites")},leadingIcon={Icon(Icons.Rounded.Star,null,Modifier.size(18.dp))})
            if(muscle!="All muscles") InputChip(true,{muscle="All muscles"},label={Text(muscle)},trailingIcon={Icon(Icons.Rounded.Close,null,Modifier.size(14.dp))})
            if(equipment!="All equipment") InputChip(true,{equipment="All equipment"},label={Text(equipment)},trailingIcon={Icon(Icons.Rounded.Close,null,Modifier.size(14.dp))})
        }
        if(filter) Column(Modifier.padding(horizontal=18.dp)) {FilterChooser("Muscle group",listOf("All muscles")+exercises.map {it.muscle}.distinct().sorted(),muscle){muscle=it};FilterChooser("Equipment",listOf("All equipment")+exercises.map {it.equipment}.distinct().sorted(),equipment){equipment=it}}
        Text("${filtered.size} exercises",Modifier.padding(horizontal=20.dp,vertical=10.dp),color=OF.muted,style=MaterialTheme.typography.labelLarge)
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=18.dp,vertical=2.dp)) {
            if(filtered.isEmpty()) item {EmptyContent(Icons.Rounded.SearchOff,"No exercises found","Try a different name or clear your filters.","Clear Filters",{query="";muscle="All muscles";equipment="All equipment";onlyFavorites=false})}
            items(filtered,key={it.id}) {e->
                Row(Modifier.fillMaxWidth().clickable {if(picking && onPick!=null)onPick(e.id) else onDetail(e.id)}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    ExerciseImage(e,Modifier.size(64.dp))
                    Column(Modifier.weight(1f)) {Text(e.name,style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis);Text("${e.muscle} · ${e.equipment}",color=OF.muted,fontSize=12.sp,maxLines=2)}
                    IconButton({if(picking && onPick!=null)onDetail(e.id) else onFavorite(e.id)}) {Icon(if(picking) Icons.Rounded.Info else if(e.id in favorites)Icons.Rounded.Star else Icons.Rounded.StarOutline,if(picking)"Exercise details" else if(e.id in favorites)"Remove favorite" else "Favorite exercise",tint=if(e.id in favorites)OF.purple else OF.muted)}
                }
                HorizontalDivider(color=OF.line.copy(alpha=0.5f))
            }
        }
    }
}
@Composable private fun FilterChooser(title:String,values:List<String>,selected:String,onSelect:(String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box {TextButton({expanded=true}){Text("$title: $selected");Icon(Icons.Rounded.ExpandMore,null)};DropdownMenu(expanded,{expanded=false}) {values.forEach {v->DropdownMenuItem(text={Text(v)},onClick={expanded=false;onSelect(v)})}}}
}
@Composable fun ExerciseDetail(e:Exercise,data:AppData,vm:WorkoutViewModel,onBack:()->Unit,onAdd:()->Unit) {
    var tab by rememberSaveable {mutableIntStateOf(0)}
    var frame by rememberSaveable(e.id) {mutableIntStateOf(0)}
    var playing by rememberSaveable {mutableStateOf(false)}
    val context=LocalContext.current
    val instructions=remember(e.id) { if(e.instructions.startsWith("reference/")) runCatching {context.assets.open(e.instructions).bufferedReader().use {it.readText()}}.getOrDefault("") else e.instructions }
    LaunchedEffect(playing,e.id) {while(playing && e.frames.size>1) {delay(1100);frame=(frame+1)%e.frames.size}}
    Column(Modifier.fillMaxSize()) {
        PageHeading(e.name,onBack,actions={IconButton({vm.favorite(e.id)}){Icon(if(e.id in data.favorites)Icons.Rounded.Star else Icons.Rounded.StarOutline,"Toggle favorite")}})
        SecondaryTabRow(tab,containerColor=OF.top) {listOf("Overview","Instructions","History").forEachIndexed {i,s->Tab(tab==i,{tab=i},text={Text(s)})}}
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(tab==0) {
                item {
                    if(e.video!=null) AndroidView(factory={ctx->VideoView(ctx).apply {setVideoURI(Uri.parse(e.video));setMediaController(MediaController(ctx).also {it.setAnchorView(this)})}},modifier=Modifier.fillMaxWidth().height(250.dp),onRelease={it.stopPlayback()})
                    else if(e.frames.isNotEmpty()) {
                        ExerciseImage(e,Modifier.fillMaxWidth().height(260.dp),frame)
                        if(e.frames.size>1) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                            IconButton({frame=(frame-1+e.frames.size)%e.frames.size}){Icon(Icons.Rounded.SkipPrevious,"Previous frame")}
                            IconButton({playing=!playing}){Icon(if(playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if(playing)"Pause demonstration" else "Play image demonstration")}
                            Text("${frame+1} / ${e.frames.size}",color=OF.muted)
                            IconButton({frame=(frame+1)%e.frames.size}){Icon(Icons.Rounded.SkipNext,"Next frame")}
                        }
                    } else EmptyContent(Icons.Rounded.FitnessCenter,"${e.equipment} exercise","No demonstration is installed for this exercise.")
                }
                item {SectionTitle("Muscle Groups");Text(e.muscle,style=MaterialTheme.typography.titleMedium);if(e.secondary.isNotEmpty())Text(e.secondary.joinToString(", "),color=OF.muted)}
                item {SectionTitle("Equipment");Text(e.equipment,style=MaterialTheme.typography.bodyLarge)}
                item {SectionTitle("Exercise Details");Text(if(instructions.isBlank())"Add your own technique notes in a workout, or import the exercise reference pack." else descriptionSection(instructions),color=OF.muted,style=MaterialTheme.typography.bodyLarge)}
            } else if(tab==1) {
                if(instructions.isBlank())item{EmptyContent(Icons.Rounded.Notes,"No instructions installed","This exercise can still be added and logged.")}
                else markdownItems(instructions).forEach {(heading,text)->item {if(heading!=null)Text(heading,style=MaterialTheme.typography.titleLarge) else Text(text,style=MaterialTheme.typography.bodyLarge,color=OF.muted)}}
            } else {
                val history=data.history.filter {w->w.exercises.any {it.exerciseId==e.id}}
                if(history.isEmpty())item{EmptyContent(Icons.Rounded.History,"No exercise history","Completed sets will appear here after your first workout.")}
                items(history,key={it.id}) {w->Surface(shape=OF.shape,color=OF.card){Column(Modifier.padding(16.dp)){Text(w.name,style=MaterialTheme.typography.titleMedium);Text(w.localDate().toString(),color=OF.muted);w.exercises.filter {it.exerciseId==e.id}.flatMap {it.sets}.filter {it.completed}.forEach {s->Text("${s.weightKg.displayWeight(data.preferences.pounds)} ${if(data.preferences.pounds)"lb" else "kg"} × ${s.reps}${s.rir?.let {" · $it RIR"} ?: ""}",Modifier.padding(top=8.dp))}}}}
            }
        }
        Box(Modifier.padding(18.dp)) {PrimaryAction(if(data.active==null)"Start Workout with Exercise" else "Add to Workout",onAdd,icon=Icons.Rounded.Add)}
    }
}
private fun cleanMarkdown(text:String)=text.replace(Regex("\\[([^]]+)]\\([^)]*\\)"),"$1").replace("**","").replace("__","")
private fun descriptionSection(text:String)=cleanMarkdown(text.substringAfter("## Description",text.substringBefore("## Exercise technique")).trim()).take(2400)
private fun markdownItems(text:String):List<Pair<String?,String>> = text.split(Regex("\\n(?=##? )")).map {part->val lines=part.trim().lines();if(lines.firstOrNull()?.startsWith("#")==true) listOf(lines.first().trimStart('#',' ') to "",null to cleanMarkdown(lines.drop(1).joinToString("\n")).trim()) else listOf(null to cleanMarkdown(part))}.flatten().filter {it.first!=null || it.second.isNotBlank()}

@Composable fun CustomExercise(onBack:()->Unit,onSave:(String,String,String,String)->Unit) {
    var name by rememberSaveable {mutableStateOf("")};var muscle by rememberSaveable {mutableStateOf("Other")};var equipment by rememberSaveable {mutableStateOf("Bodyweight")};var instructions by rememberSaveable {mutableStateOf("")}
    Column(Modifier.fillMaxSize()) {PageHeading("New Exercise",onBack);Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("Exercise name")},singleLine=true)
        OutlinedTextField(muscle,{muscle=it},Modifier.fillMaxWidth(),label={Text("Primary muscle")},singleLine=true)
        OutlinedTextField(equipment,{equipment=it},Modifier.fillMaxWidth(),label={Text("Equipment")},singleLine=true)
        OutlinedTextField(instructions,{instructions=it},Modifier.fillMaxWidth().heightIn(min=160.dp),label={Text("Instructions and technique notes")})
    };Box(Modifier.padding(18.dp)){PrimaryAction("Create Exercise",{onSave(name,muscle.ifBlank {"Other"},equipment.ifBlank {"Other"},instructions)},enabled=name.isNotBlank())}}
}
@Composable fun RoutineBuilder(exercises:List<Exercise>,onBack:()->Unit,onSave:(String,List<String>)->Unit) {
    var name by rememberSaveable {mutableStateOf("")};var query by rememberSaveable {mutableStateOf("")};var selected by rememberSaveable {mutableStateOf(listOf<String>())}
    Column(Modifier.fillMaxSize()) {PageHeading("Create Workout",onBack);OutlinedTextField(name,{name=it},Modifier.fillMaxWidth().padding(18.dp),label={Text("Workout name")},singleLine=true);OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=18.dp),placeholder={Text("Search exercises")},leadingIcon={Icon(Icons.Rounded.Search,null)},singleLine=true);Text("${selected.size} selected",Modifier.padding(18.dp),color=OF.muted)
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=18.dp)) {items(exercises.filter {it.name.contains(query,true)}.sortedBy {it.name},key={it.id}) {e->ListItem(headlineContent={Text(e.name)},supportingContent={Text("${e.muscle} · ${e.equipment}")},leadingContent={ExerciseImage(e,Modifier.size(48.dp))},trailingContent={Checkbox(e.id in selected,{selected=if(e.id in selected)selected-e.id else selected+e.id})},modifier=Modifier.clickable {selected=if(e.id in selected)selected-e.id else selected+e.id},colors=ListItemDefaults.colors(containerColor=OF.background))}}
        Box(Modifier.padding(18.dp)){PrimaryAction("Save Workout",{onSave(name,selected)},enabled=name.isNotBlank() && selected.isNotEmpty())}
    }
}
