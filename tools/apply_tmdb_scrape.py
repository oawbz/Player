"""Apply a reviewed TMDB stage after source hashes and backups have been checked."""
from pathlib import Path
import argparse,json,hashlib,xml.etree.ElementTree as ET
from PIL import Image

def sha(data):return hashlib.sha256(data).hexdigest()
def prepare(b):
 rows=json.loads((b/'results.json').read_text());plan=[]
 for r in rows:
  if r['status']!='staged':continue
  rel=Path(r['category'])/r['name'];folder=Path(r['folder']);stage=b/'staged'/rel;old=b/'backup'/rel
  data=(stage/'movie.nfo').read_bytes();x=ET.fromstring(data);assert x.tag=='movie' and x.findtext('plot');assert len(data)<=512*1024;assert x.find("uniqueid[@type='tmdb']").text==str(r['tmdb_id'])
  if r['name']=='树上有个好地方2：美术老师的放羊班':assert x.findtext('premiered')=='2023-07-28'
  plan.append(dict(name=r['name'],kind='nfo',source=str(stage/'movie.nfo'),target=str(folder/'movie.nfo'),before=r.get('movie.nfo_sha256'),after=sha(data)))
  if r.get('poster_staged'):
   with Image.open(stage/'poster.jpg') as im:im.verify()
   with Image.open(stage/'poster.jpg') as im:new=im.size;assert im.format=='JPEG' and min(new)>=300
   prior=None
   if (old/'poster.jpg').exists():
    try:
     with Image.open(old/'poster.jpg') as im:im.load();prior=im.size
    except Exception:pass
   reason=''
   if r['name']=='树上有个好地方2：美术老师的放羊班':reason='保留用户指定海报'
   elif prior and new[0]*new[1]<prior[0]*prior[1]:reason='TMDB 原图像素少于现有有效海报'
   r['poster_action']='keep' if reason else 'replace';r['poster_keep_reason']=reason;r['previous_poster_dimensions']=prior
   if not reason:plan.append(dict(name=r['name'],kind='poster',source=str(stage/'poster.jpg'),target=str(folder/'poster.jpg'),before=r.get('poster.jpg_sha256'),after=sha((stage/'poster.jpg').read_bytes())))
 (b/'results-reviewed.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2));(b/'apply-plan.json').write_text(json.dumps(plan,ensure_ascii=False,indent=2));print(json.dumps({'matched':sum(r['status']=='staged' for r in rows),'nfo_updates':sum(r['kind']=='nfo' for r in plan),'poster_updates':sum(r['kind']=='poster' for r in plan),'posters_kept':sum(r.get('poster_action')=='keep' for r in rows),'old_plots_kept':sum(not r.get('plot_updated') for r in rows)},ensure_ascii=False),flush=True)
def apply(b):
 plan=json.loads((b/'apply-plan.json').read_text());verified=[]
 for r in plan:
  target=Path(r['target']);actual=target.read_bytes() if target.exists() else None;assert (sha(actual) if actual is not None else None)==r['before'],'source changed: '+r['name']+' '+r['kind'];data=Path(r['source']).read_bytes();assert sha(data)==r['after'];target.write_bytes(data);actual=target.read_bytes();assert sha(actual)==r['after']
  if r['kind']=='nfo':assert ET.fromstring(actual).findtext('plot')
  verified.append(dict(name=r['name'],kind=r['kind'],sha256=r['after'],verified=True));(b/'applied.json').write_text(json.dumps(verified,ensure_ascii=False,indent=2));print('verified',len(verified),r['name'],r['kind'],flush=True)
 print('COMPLETE',len(verified),flush=True)
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('folder');p.add_argument('--apply',action='store_true');a=p.parse_args();b=Path(a.folder);apply(b) if a.apply else prepare(b)
