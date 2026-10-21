package org.openfactor.app

import android.graphics.Typeface
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

object OF {
    val background=Color(0xFF131313)
    val top=Color(0xFF202020)
    val card=Color(0xFF202020)
    val elevated=Color(0xFF353535)
    val muted=Color(0xFFB8B8BC)
    val line=Color(0xFF414141)
    val blue=Color(0xFF3982FF)
    val green=Color(0xFF91D8AD)
    val purple=Color(0xFFB794F4)
    val shape=RoundedCornerShape(16.dp)
}
@Composable fun OpenFactorTheme(content:@Composable ()->Unit) {
    val context=LocalContext.current
    val body=remember { runCatching { FontFamily(Typeface.createFromAsset(context.assets,"reference/fonts/DMSans-VariableFont_opsz,wght.ttf")) }.getOrDefault(FontFamily.SansSerif) }
    MaterialTheme(colorScheme=darkColorScheme(primary=Color.White,onPrimary=Color.Black,background=OF.background,surface=OF.card,surfaceVariant=OF.elevated,onSurface=Color.White,onSurfaceVariant=OF.muted,outline=OF.line,secondary=OF.blue,error=Color(0xFFFF9696)),
        typography=Typography().let { t -> t.copy(displaySmall=t.displaySmall.copy(fontFamily=body,fontWeight=FontWeight.Bold),headlineLarge=t.headlineLarge.copy(fontFamily=body,fontWeight=FontWeight.Bold),headlineMedium=t.headlineMedium.copy(fontFamily=body,fontWeight=FontWeight.Bold),titleLarge=t.titleLarge.copy(fontFamily=body,fontWeight=FontWeight.SemiBold),titleMedium=t.titleMedium.copy(fontFamily=body),bodyLarge=t.bodyLarge.copy(fontFamily=body),bodyMedium=t.bodyMedium.copy(fontFamily=body),labelLarge=t.labelLarge.copy(fontFamily=body)) },content=content)
}
/** Adapted from Nuvio's NavigationBar.kt geometry, selected surface and 300ms motion.
 * Copyright Nuvio contributors; modifications Open Factor contributors. GPL-3.0. */
@Composable fun NuvioWorkoutNavigation(selected:Int,onSelect:(Int)->Unit) {
    val labels=listOf("Dashboard","Workout","Exercises","Settings")
    val icons=listOf(Icons.Rounded.Dashboard,Icons.Rounded.FitnessCenter,Icons.Rounded.Search,Icons.Rounded.AccountCircle)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start=28.dp,end=28.dp,bottom=14.dp),contentAlignment=Alignment.BottomCenter) {
        BoxWithConstraints(Modifier.widthIn(max=440.dp).fillMaxWidth().height(66.dp).clip(RoundedCornerShape(50)).background(Color(0xFF1C1C1E)).border(1.dp,Brush.verticalGradient(listOf(Color(0xFF9B786E),Color(0xFF2E2929))),RoundedCornerShape(50)).selectableGroup()) {
            val slot=(maxWidth-8.dp)/4
            val offset by animateDpAsState(4.dp+slot*selected,tween(300),label="navigation pill")
            Box(Modifier.offset(x=offset,y=4.dp).width(slot).height(58.dp).clip(RoundedCornerShape(50)).background(Color(0xFF393939)))
            Row(Modifier.fillMaxSize().padding(4.dp)) { labels.forEachIndexed { i,label ->
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50)).selectable(selected==i,role=Role.Tab,onClick={onSelect(i)}),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Icon(icons[i],null,Modifier.size(26.dp),tint=if(i==selected) Color.White else Color(0xFF92969C))
                    Text(label,fontSize=12.sp,lineHeight=16.sp,fontWeight=if(i==selected) FontWeight.Bold else FontWeight.Normal,color=if(i==selected) Color.White else Color(0xFFA1A4AA),maxLines=1)
                }
            } }
        }
    }
}
@Composable fun RoundAction(icon:ImageVector,description:String,onClick:()->Unit) {
    IconButton(onClick,Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(OF.elevated)) { Icon(icon,description,Modifier.size(24.dp)) }
}
@Composable fun PageHeading(title:String,back:(()->Unit)?=null,actions:@Composable RowScope.()->Unit={}) {
    val context=LocalContext.current
    val display=remember { runCatching { FontFamily(Typeface.createFromAsset(context.assets,"reference/fonts/MacroSans-VF.ttf")) }.getOrDefault(FontFamily.SansSerif) }
    Row(Modifier.fillMaxWidth().background(OF.top).padding(horizontal=18.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        if(back!=null) IconButton(back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,"Back") }
        Text(title,Modifier.weight(1f),fontFamily=display,fontSize=if(back==null) 25.sp else 20.sp,fontWeight=FontWeight.Bold,maxLines=2)
        actions()
    }
}
@Composable fun SectionTitle(title:String,action:String?=null,onAction:()->Unit={}) {
    Row(Modifier.fillMaxWidth().padding(top=24.dp,bottom=14.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
        if(action!=null) TextButton(onAction) { Text(action,color=OF.muted) }
    }
}
@Composable fun PrimaryAction(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,icon:ImageVector?=null) {
    Button(onClick,modifier.fillMaxWidth().heightIn(min=52.dp),enabled=enabled,shape=RoundedCornerShape(8.dp),colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Color.Black)) {
        if(icon!=null) {Icon(icon,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp))};Text(text,style=MaterialTheme.typography.titleMedium)
    }
}
@Composable fun EmptyContent(icon:ImageVector,title:String,body:String,action:String?=null,onAction:()->Unit={}) {
    Column(Modifier.fillMaxWidth().padding(vertical=32.dp,horizontal=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(icon,null,Modifier.size(40.dp),tint=OF.muted)
        Text(title,style=MaterialTheme.typography.titleLarge)
        Text(body,color=OF.muted,style=MaterialTheme.typography.bodyLarge,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        if(action!=null) TextButton(onAction) {Text(action)}
    }
}
@Composable fun ExerciseImage(exercise:Exercise,modifier:Modifier=Modifier,frame:Int=0) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(OF.elevated),contentAlignment=Alignment.Center) {
        if(exercise.frames.isNotEmpty()) AsyncImage(model="file:///android_asset/"+exercise.frames[frame.coerceIn(0,exercise.frames.lastIndex)],contentDescription=exercise.name,modifier=Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Fit)
        else Icon(Icons.Rounded.FitnessCenter,null,Modifier.size(28.dp),tint=OF.muted)
    }
}
@Composable fun ConfirmDialog(title:String,text:String,confirm:String,onDismiss:()->Unit,onConfirm:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(text)},confirmButton={TextButton(onConfirm){Text(confirm)}},dismissButton={TextButton(onDismiss){Text("Cancel")}})
}
@Composable fun NameDialog(title:String,initial:String="",onDismiss:()->Unit,onSave:(String)->Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={OutlinedTextField(name,{name=it},label={Text("Name")},singleLine=true)},confirmButton={TextButton({onSave(name.trim())},enabled=name.isNotBlank()){Text("Save")}},dismissButton={TextButton(onDismiss){Text("Cancel")}})
}
