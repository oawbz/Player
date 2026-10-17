"""Stage TMDB-only NFO/posters; credentials stay outside the repository."""
import argparse,hashlib,io,json,re,time,unicodedata,urllib.request,urllib.parse,urllib.error,xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image
KEY=Path.home()/'.config/davplayer/tmdb.json'
def norm(s):return ''.join(c for c in unicodedata.normalize('NFKC',s or '').lower() if c.isalnum())
def digest(b):return hashlib.sha256(b).hexdigest()
def save(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2))
def run(root,out,resume=False):
 key=json.loads(KEY.read_text())['api_key'];out.mkdir(parents=True,exist_ok=True)
 def api(route,**params):
  params={'language':'zh-CN',**params};cache=out/'api'/(route.replace('/','_')+'-'+digest(json.dumps(params,sort_keys=True).encode())[:12]+'.json')
  if cache.exists():return json.loads(cache.read_text())
  for attempt in range(3):
   try:
    req=urllib.request.Request('https://api.themoviedb.org/3/'+route+'?'+urllib.parse.urlencode(dict(params,api_key=key)),headers={'Accept':'application/json'})
    with urllib.request.urlopen(req,timeout=25) as f:d=json.load(f)
    save(cache,d);time.sleep(.25);return d
   except urllib.error.HTTPError as e:
    if e.code in [429,503] and attempt<2:time.sleep(min(30,max(1,int(e.headers.get('Retry-After','3')))));continue
    raise RuntimeError('TMDB HTTP '+str(e.code)) from None
   except (TimeoutError,urllib.error.URLError):
    if attempt<2:time.sleep(2);continue
    raise RuntimeError('TMDB connection failed') from None
  raise RuntimeError('TMDB retries exhausted')
 config=api('configuration');image_base=config['images']['secure_base_url'];rows=[]
 if resume and (out/'inventory.json').exists():rows=json.loads((out/'inventory.json').read_text())
 else:
  for cat in root.iterdir():
   if cat.name.startswith('.') or not cat.is_dir():continue
   for folder in cat.iterdir():
    if folder.name.startswith('.') or not folder.is_dir():continue
    r={'category':cat.name,'name':folder.name,'folder':str(folder)};rel=Path(cat.name)/folder.name
    for name in ['movie.nfo','poster.jpg']:
     p=folder/name
     if p.exists():
      data=p.read_bytes();dst=out/'backup'/rel/name;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(data);r[name+'_sha256']=digest(data)
    nfo=out/'backup'/rel/'movie.nfo'
    if nfo.exists():
     try:
      x=ET.fromstring(nfo.read_bytes());r.update(title=x.findtext('title'),year=x.findtext('year'),old_plot_length=len(x.findtext('plot') or ''));r['old_tmdb']=next((i.text for i in x.findall('uniqueid') if i.get('type')=='tmdb'),None)
     except ET.ParseError:r['nfo_invalid']=True
    rows.append(r);print('inventory',len(rows),folder.name,flush=True);save(out/'inventory.json',rows)
 results=json.loads((out/'results.json').read_text()) if resume and (out/'results.json').exists() else []
 completed={r['name'] for r in results if r['status']=='staged'}
 results=[r for r in results if r['name'] in completed]
 for w in rows:
  if w['name'] in completed:continue
  r=dict(w);name=(w.get('title') or w['name']).strip();query=re.sub(r'\s*\(\d{4}\).*','',name);query=re.sub(r'\s+4K.*','',query)
  for prefix in ['射雕英雄传之东成西就','大话西游之大圣娶亲']:
   if query.startswith(prefix):query=prefix
  season=2 if '第二季' in query else 1 if '第一季' in query else None;query=re.sub(r'\s*第[一二]季','',query).strip();kind='movie' if w['category']=='电影' else 'tv';r.update(query=query,media_type=kind,season=season)
  try:
   candidates=api('search/'+kind,query=query,include_adult='false').get('results',[]);r['candidates']=[{'id':c['id'],'title':c.get('title') or c.get('name'),'original_title':c.get('original_title') or c.get('original_name'),'date':c.get('release_date') or c.get('first_air_date'),'overview_length':len(c.get('overview') or '')} for c in candidates[:10]]
   aliases={norm(query)}
   if query=='镖人':aliases.add(norm('镖人：风起大漠'))
   if query=='树上有个好地方2：美术老师的放羊班':aliases.add(norm('美术老师的放羊班'))
   exact=[c for c in candidates if norm(c.get('title') or c.get('name')) in aliases or norm(c.get('original_title') or c.get('original_name')) in aliases]
   year=int(w['year']) if str(w.get('year','')).isdigit() else None
   def delta(c):
    date=c.get('release_date') or c.get('first_air_date') or '';return abs(int(date[:4])-year) if year and date[:4].isdigit() else 999
   if season:
    eligible=exact
   else:
    eligible=[c for c in exact if not year or delta(c)<=1];eligible.sort(key=delta)
    if len(eligible)>1 and delta(eligible[0])<delta(eligible[1]):eligible=eligible[:1]
   if not eligible and len(exact)==1 and kind=='movie' and year:
    releases=api('movie/'+str(exact[0]['id'])+'/release_dates')
    dates=[x['release_date'][:10] for country in releases.get('results',[]) if country.get('iso_3166_1')=='CN' for x in country.get('release_dates',[]) if x.get('release_date','').startswith(str(year))]
    if dates:eligible=exact;r['effective_release_date']=min(dates);r['match_note']='Matched Chinese release record rather than conflicting primary release date'
   if len(eligible)!=1:r['status']='unmatched' if not candidates else 'review';results.append(r);save(out/'results.json',results);print(r['status'],w['name'],flush=True);continue
   c=eligible[0];d=api(kind+'/'+str(c['id']),append_to_response='credits,external_ids');r.update(tmdb_id=c['id'],tmdb_title=d.get('title') or d.get('name'),tmdb_date=d.get('release_date') or d.get('first_air_date'),source_url='https://www.themoviedb.org/'+kind+'/'+str(c['id']))
   content=d
   if season:
    sea=api('tv/'+str(c['id'])+'/season/'+str(season));sy=(sea.get('air_date') or '')[:4]
    if year and sy and abs(int(sy)-year)>1:raise ValueError('season year mismatch')
    content=sea;r['source_url']+='/season/'+str(season)
   plot=content.get('overview') or '';has_chinese=bool(re.search('[\u3400-\u9fff]',plot));r['plot_length']=len(plot);rel=Path(w['category'])/w['name'];stage=out/'staged'/rel;stage.mkdir(parents=True,exist_ok=True)
   old=out/'backup'/rel/'movie.nfo';x=ET.fromstring(old.read_bytes()) if old.exists() and not w.get('nfo_invalid') else ET.Element('movie');x.tag='movie'
   def setv(tag,value):
    if value is None or value=='':return
    els=x.findall(tag)
    for e in els:x.remove(e)
    ET.SubElement(x,tag).text=str(value)
   setv('title',w.get('title') or d.get('title') or d.get('name'));setv('originaltitle',d.get('original_title') or d.get('original_name'))
   if has_chinese:setv('plot',plot)
   r['plot_updated']=has_chinese
   date=r.get('effective_release_date') or content.get('air_date') or d.get('release_date') or d.get('first_air_date');setv('year',date[:4] if date else w.get('year'));setv('premiered',date);setv('runtime',d.get('runtime') or next(iter(d.get('episode_run_time') or []),None));setv('website',r['source_url']);setv('studio',(d.get('production_companies') or [{}])[0].get('name'))
   if d.get('genres'):
    for e in x.findall('genre'):x.remove(e)
    for g in d['genres']:ET.SubElement(x,'genre').text=g['name']
   for e in x.findall('uniqueid'):
    if e.get('type')=='tmdb':x.remove(e)
    else:e.set('default','false')
   ET.SubElement(x,'uniqueid',type='tmdb',default='true').text=str(c['id']);imdb=(d.get('external_ids') or {}).get('imdb_id') or d.get('imdb_id')
   if imdb:
    for e in x.findall('uniqueid'):
     if e.get('type')=='imdb':x.remove(e)
    ET.SubElement(x,'uniqueid',type='imdb').text=imdb
   ratings=x.find('ratings')
   if ratings is None:ratings=ET.SubElement(x,'ratings')
   for e in list(ratings):
    if e.get('name')=='tmdb':ratings.remove(e)
    else:e.set('default','false')
   if d.get('vote_count'):
    rating=ET.SubElement(ratings,'rating',name='tmdb',max='10',default='true');ET.SubElement(rating,'value').text=str(d.get('vote_average',0));ET.SubElement(rating,'votes').text=str(d['vote_count'])
   cast=(d.get('credits') or {}).get('cast') or []
   if cast:
    for e in x.findall('actor'):x.remove(e)
    for i,a in enumerate(cast[:30]):
     e=ET.SubElement(x,'actor');ET.SubElement(e,'name').text=a['name'];ET.SubElement(e,'role').text=a.get('character') or '';ET.SubElement(e,'order').text=str(i)
   directors=[a['name'] for a in (d.get('credits') or {}).get('crew',[]) if a.get('job')=='Director']
   if directors:
    for e in x.findall('director'):x.remove(e)
    for name in dict.fromkeys(directors):ET.SubElement(x,'director').text=name
   ET.indent(x);ET.ElementTree(x).write(stage/'movie.nfo',encoding='utf-8',xml_declaration=True);ET.parse(stage/'movie.nfo');r['nfo_staged']=True;r['cast_count']=len(cast)
   poster=content.get('poster_path') or d.get('poster_path');r['poster_staged']=False
   if poster:
    try:
     url=image_base+'original'+poster
     with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'DavPlayer-personal-metadata/1.0'}),timeout=30) as f:data=f.read(30*1024*1024)
     im=Image.open(io.BytesIO(data));im.load();assert min(im.size)>=300;rgb=im.convert('RGB');rgb.save(stage/'poster.jpg','JPEG',quality=95);r['poster_staged']=True;r['poster_dimensions']=list(rgb.size)
    except Exception as e:r['poster_error']=type(e).__name__
   r['status']='staged'
  except Exception as e:r['status']='error';r['error']=str(e) if isinstance(e,(RuntimeError,ValueError)) else type(e).__name__
  results.append(r);save(out/'results.json',results);print(r['status'],w['name'],'plot',r.get('plot_length',0),'poster',r.get('poster_staged',False),flush=True)
 save(out/'summary.json',{'total':len(results),'staged':sum(r['status']=='staged' for r in results),'review':sum(r['status']=='review' for r in results),'unmatched':sum(r['status']=='unmatched' for r in results),'errors':sum(r['status']=='error' for r in results)})
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--root',default='/Volumes/影视');p.add_argument('--out',required=True);p.add_argument('--resume',action='store_true');a=p.parse_args();run(Path(a.root),Path(a.out),a.resume)
