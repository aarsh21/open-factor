#!/usr/bin/env python3
"""Build an ignored, local-only reference pack from a caller-supplied Workouts APK.

Standard library only; no device access, network fetches or target execution.
Preserves raw catalog/group metadata and writes a small application projection.
Imported content retains its own ownership; this script does not grant a license.
"""
import argparse
from collections import Counter
from hashlib import sha256
import json
from pathlib import Path, PurePosixPath
import re
from zipfile import ZipFile

CATALOG = 'assets/flutter_assets/packages/macrofactor/assets/import/app_file.json'
ASSETS = 'assets/flutter_assets/assets/'
FONTS = 'assets/flutter_assets/packages/macrofactor/fonts/'
ID = re.compile(r'^(?:preferred_)?([0-9a-f]{32})(?:_[a-z])?$')

def make_browse_classifier(index):
    # Specimen-bound translation of the native five-category predicate.
    # This classifies bundled records, not the authenticated effective catalog.
    categories={v['name']:k for k,v in index.items() if v.get('type')=='appEquipmentCategory'}
    def ids(names): return {categories[n] for n in names if n in categories}
    machines=ids(['Cable Machines','Pin Loaded Machines','Plate Loaded Machines'])
    personal=ids(['Body Weights','Accessories & Functional'])
    free=ids(['Free Weights','Loadable Bars','Fixed Weight Bars','Bands & Ropes','Loadable Accessories'])
    bands=categories.get('Bands & Ropes');cardio_id=categories.get('Cardio')
    def members(g): return index[g].get('equipment',[g])
    def has(g, category_ids): return any(index[i].get('category') in category_ids for i in members(g))
    def all_bands(g): return all(index[i].get('category')==bands for i in members(g))
    def classify(e):
        resistance=e.get('resistanceEquipmentGroupIds',[]);support=e.get('supportEquipmentGroupIds',[])
        cardio=any(has(g,{cardio_id}) for g in resistance) and not any(has(g,{cardio_id}) for g in support)
        result=[]
        if not cardio and '25a5c6f170d880aa9232e07fc8128d85' not in e.get('exerciseMetrics',[]) and any(members(g)==['19e5c6f170d880ec8238f837c5284019'] or has(g,personal) or all_bands(g) for g in resistance):result.append('Bodyweight & Personal')
        if any(not has(g,machines) and any(i!='2b25c6f170d8808c902ad0bc95551783' and index[i].get('category') in free for i in members(g)) and not all_bands(g) for g in resistance) and not any(has(g,personal) for g in resistance):result.append('Free Weights')
        if any(has(g,machines) for g in resistance):result.append('Machines')
        if cardio:result.append('Cardio')
        return result or ['Other']
    return classify

def build(apk: Path, dest: Path, video_directory: Path | None = None, public_data_output: Path | None = None, facts_only: bool = False):
    if facts_only and (public_data_output is None or video_directory is not None):
        raise ValueError('--facts-only requires --public-data-output and excludes --video-directory')
    if dest.exists() and any(dest.iterdir()):
        raise ValueError(f'Refusing to overwrite an existing pack: {dest}; choose a new --output')
    dest.mkdir(parents=True, exist_ok=True)
    provenance=[]
    with ZipFile(apk) as z:
        manifest=[] if facts_only else json.loads(z.read('assets/flutter_assets/FontManifest.json'))
        extra_fonts={'assets/flutter_assets/'+f['asset']:PurePosixPath('fonts')/PurePosixPath(f['asset']).name
            for family in manifest for f in family['fonts']}
        if not facts_only:
            (dest/'font-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
        raw=z.read(CATALOG)
        catalog=json.loads(raw)
        index=catalog['uuidIndex']
        pictures={}
        markdown={}
        for member in ([] if facts_only else z.infolist()):
            name=member.filename
            local=None
            if name.startswith(ASSETS) and not member.is_dir():
                local=PurePosixPath(name.removeprefix(ASSETS))
            elif name in extra_fonts and not member.is_dir():
                local=extra_fonts[name]
            if local is None: continue
            if local.is_absolute() or '..' in local.parts: raise ValueError('Unsafe APK member path')
            data=z.read(member)
            path=dest.joinpath(*local.parts);path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
            provenance.append({'apk_member':name,'asset_path':'reference/'+str(local),'sha256':sha256(data).hexdigest(),'bytes':len(data)})
            match=ID.fullmatch(local.stem)
            if match:
                key=match.group(1)
                if local.parts[0]=='exercises' and local.suffix.lower()=='.webp':pictures.setdefault(key,[]).append('reference/'+str(local))
                if local.parts[0]=='markdown' and local.suffix=='.md':markdown.setdefault(key,[]).append('reference/'+str(local))
        def entities(record, field): return [index[i]['name'] for i in record.get(field,[]) if i in index]
        def equipment(record):
            names=entities(record,'resistanceEquipmentGroupIds')
            # Preserve every precise option in equipmentOptions and the raw catalog.
            text=(' '.join(names)+' '+record['name']).lower()
            for token,label in [('bodyweight only','Bodyweight'),('dumbbell','Dumbbell'),('barbell','Barbell'),('cable','Cable'),('kettlebell','Kettlebell'),('smith','Smith Machine'),('machine','Machine'),('band','Band')]:
                if token in text:return label
            return names[0] if len(names)==1 else ('Bodyweight' if not names and (record.get('bodyweight') or 0)>0 else 'Other')
        classify_browse=make_browse_classifier(index)
        projected=[]
        video_count=0
        def frame_order(path):
            stem=PurePosixPath(path).stem
            return (not stem.startswith('preferred_'), stem.rsplit('_',1)[-1])
        for record in catalog['exercises']:
            eid=record['id']
            muscles=entities(record,'primaryFeatureMuscle')
            frames=sorted(pictures.get(eid,[]),key=frame_order)
            objects=[]
            clips=[]
            for frame in frames:
                suffix=PurePosixPath(frame).stem.rsplit('_',1)[-1]
                object_path=f'videos/{eid}_{suffix}.mp4'
                objects.append(object_path)
                clip=''
                if video_directory is not None:
                    source=video_directory/object_path
                    if not source.is_file():source=video_directory/PurePosixPath(object_path).name
                    if source.is_file():
                        data=source.read_bytes()
                        # Verify an ISO-BMFF file signature, not an HTML/error response.
                        if len(data)<12 or data[4:8]!=b'ftyp':raise ValueError(f'Not an MP4: {source}')
                        target=dest/object_path;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
                        clip='reference/'+object_path
                        provenance.append({'local_file':str(source.resolve()),'asset_path':clip,'sha256':sha256(data).hexdigest(),'bytes':len(data)})
                        video_count+=1
                clips.append(clip)
            projected.append({'id':eid,'name':record['name'],'muscle':muscles[0] if muscles else 'Other',
                'muscles':muscles,'secondary':entities(record,'secondaryFeatureMuscle'),
                'properties':{label: ', '.join(str(index[i]['name']) for i in (record.get(field,[]) if isinstance(record.get(field),list) else [record.get(field)]) if i in index) or 'None'
                    for label,field in [('Type','exerciseType'),('Region','regionTrained'),('Movement Patterns','movementPattern'),('Laterality','laterality'),('Primary Joint Action','primaryJointAction'),('Secondary Joint Action','secondaryJointAction'),('Metrics','exerciseMetrics'),('Range of Motion','rom'),('Stability','stability')]},
                'bodyweight':record.get('bodyweight') or 0.0,'searchBoost':record.get('searchBoostValue') or 0.0,
                # Preserve original descriptor IDs, null ranks, empty lists, and absent fields.
                # These are generator facts, not searchBoost or inferred exercise classifications.
                'generationFacts':{field:record[field] for field in (
                    'exerciseClassificationStrength','exerciseClassificationHypertrophy','exclusionGroupings',
                    'strengthPrimaryCompoundRecommendationLevel','strengthSecondaryCompoundRecommendationLevel',
                    'strengthAccessoryRecommendationLevel','hypertrophyPrimaryCompoundRecommendationLevel',
                    'hypertrophySecondaryCompoundRecommendationLevel','hypertrophyAccessoryRecommendationLevel') if field in record},
                'browseCategories':classify_browse(record),
                'equipment':equipment(record),'equipmentOptions':entities(record,'resistanceEquipmentGroupIds'),
                'supportEquipmentOptions':entities(record,'supportEquipmentGroupIds'),
                'aliases':entities(record,'alternativeName'),
                'frames':frames,'clips':clips,'videoObjectPaths':objects,
                'instructions':next(iter(markdown.get(eid,[])),''),
                'video':None,'videoStatus':'local' if any(clips) else 'static-path-only'})
        if not facts_only:
            (dest/'catalog.json').write_text(json.dumps(projected,ensure_ascii=False,indent=2)+'\n')
            (dest/'source-catalog.json').write_bytes(raw)
        if public_data_output is not None:
            # Descriptive exercise/equipment facts only. No instruction prose, media, fonts or animations.
            public_data_output.mkdir(parents=True,exist_ok=True)
            public=[{k:v for k,v in e.items() if k not in {'frames','clips','instructions','video','videoObjectPaths','videoStatus'}} for e in projected]
            (public_data_output/'catalog.json').write_text(json.dumps(public,ensure_ascii=False,indent=2)+'\n')
            equipment_fields={'type','name','weightLb','weightKg','weightIncrementLb','weightIncrementKg','weightSelectLb','weightSelectKg','pluralOf','category','commercialGym','warehouseGym','localGym','garageGym','homeGym','plateCalculatorLaterality','equipment'}
            selected={k:v for k,v in index.items() if v.get('type') in {'equipment','resistanceEquipmentGroup','supportEquipmentGroup'}}
            category_ids={e.get('category') for e in selected.values() if e.get('category')}
            selected.update({k:index[k] for k in category_ids})
            metadata={'uuidIndex':{k:{field:value for field,value in v.items() if field in equipment_fields} for k,v in selected.items()},
                'exercises':[{k:v for k,v in e.items() if k in {'id','resistanceEquipmentGroupIds','supportEquipmentGroupIds'}} for e in catalog['exercises']]}
            (public_data_output/'equipment.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n')
            (public_data_output/'catalog-provenance.json').write_text(json.dumps({'source_apk_sha256':sha256(apk.read_bytes()).hexdigest(),
                'source_catalog_sha256':sha256(raw).hexdigest(),'exercises':len(public),'equipment_entities':sum(v.get('type')=='equipment' for v in index.values()),
                'scope':'Descriptive exercise/equipment facts. Source instructions, media, fonts and animations excluded.',
                'classification':'Native predicate applied to bundled metadata. Authenticated effective membership remains unverified; static bodyweight297/free530 versus captured294/527.'},indent=2)+'\n')
    report={'source_apk_sha256':sha256(apk.read_bytes()).hexdigest(),'catalog_sha256':sha256(raw).hexdigest(),
        'exercises':len(projected),'with_images':sum(bool(e['frames']) for e in projected),
        'with_instructions':sum(bool(e['instructions']) for e in projected),'videos':video_count,
        'static_video_paths':sum(len(e['videoObjectPaths']) for e in projected),
        'media_selection':'Exact filename ID joins. Static source ordering: preferred thumbnail first, then alphabetical suffix. Object paths do not establish remote access or playback.',
        'license':'Original ownership retained; not relicensed as GPL by this importer.', 'files':provenance}
    (dest/'provenance.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k!='files'},indent=2))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk',type=Path)
    parser.add_argument('--output',type=Path,default=Path(__file__).resolve().parents[1]/'openfactor/app/src/main/assets/reference')
    parser.add_argument('--video-directory',type=Path,help='Optional authorized local MP4 export, flat or videos/ subdirectory')
    parser.add_argument('--public-data-output',type=Path,help='Optional directory for descriptive facts only; no source prose/media/fonts')
    parser.add_argument('--facts-only',action='store_true',help='Write only public facts and provenance; do not extract source media, prose, fonts or raw catalog')
    args=parser.parse_args()
    build(args.apk,args.output,args.video_directory,args.public_data_output,args.facts_only)
