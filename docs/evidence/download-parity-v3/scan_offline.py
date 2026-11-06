from pathlib import Path
import struct,re,json
results=[];defined=[]
for p in Path('/tmp/re/yt-sub').glob('classes*.dex'):
 b=p.read_bytes();ns,so=struct.unpack_from('<II',b,0x38);nt,to=struct.unpack_from('<II',b,0x40);nm,mo=struct.unpack_from('<II',b,0x58);nc,co=struct.unpack_from('<II',b,0x60)
 def u(o):
  v=0;s=0
  while True:
   n=b[o];o+=1;v|=(n&127)<<s
   if n<128:return v,o
   s+=7
 def st(i):
  o=struct.unpack_from('<I',b,so+i*4)[0];_,o=u(o);return b[o:b.index(0,o)].decode('utf-8','replace')
 types=[st(struct.unpack_from('<I',b,to+i*4)[0]) for i in range(nt)]
 methods=[struct.unpack_from('<HHI',b,mo+i*8) for i in range(nm)]
 target={i:st(m[2]) for i,m in enumerate(methods) if types[m[0]]=='Lcom/google/android/apps/youtube/app/offline/ui/OfflineArrowView;' and st(m[2]) in ('d','f','g','h','i','j','k')}
 pattern=re.compile(rb'[\x6e-\x72\x74-\x78].(?:'+b'|'.join(re.escape(struct.pack('<H',i)) for i in target)+rb')',re.S) if target else None
 for ci in range(nc):
  vals=struct.unpack_from('<8I',b,co+ci*32);cl=types[vals[0]];cd=vals[6]
  if '/offline/' in cl:defined.append({'dex':p.name,'class':cl})
  if not cd or pattern is None:continue
  size=[]
  for _ in range(4):v,cd=u(cd);size.append(v)
  for _ in range(size[0]+size[1]):_,cd=u(cd);_,cd=u(cd)
  for n in size[2:]:
   mi=0
   for _ in range(n):
    delta,cd=u(cd);mi+=delta;access,cd=u(cd);code,cd=u(cd)
    if not code:continue
    count=struct.unpack_from('<I',b,code+12)[0];ins=b[code+16:code+16+2*count]
    for m in pattern.finditer(ins):
     if m.start()%2 or m.start()+6>len(ins):continue
     target_id=struct.unpack_from('<H',m.group(),2)[0]
     results.append({'dex':p.name,'class':cl,'method':st(methods[mi][2]),'code_offset':hex(code),'candidate_instruction':hex(m.start()//2),'target':'OfflineArrowView.'+target[target_id]})
Path('/tmp/re/download-deep/offline-call-candidates.json').write_text(json.dumps(results,indent=2)+'\n');Path('/tmp/re/download-deep/offline-class-inventory.json').write_text(json.dumps(defined,indent=2)+'\n')
print('Candidate callsites:',len(results),'named offline class definitions:',len(defined))
for r in results[:24]:print(r)
