"""Ask (youchat) probe. Reads the FlufPlay cookie from the phone into memory only; never prints/stores it.
Step 1: does the FlufPlay-style `next` response contain the Ask panel + token?  Step 2: send ONE question via get_panel
and save a redacted copy of the response."""
import re, json, time, hashlib, subprocess, resource, urllib.request, urllib.error, os, xml.etree.ElementTree as ET
resource.setrlimit(resource.RLIMIT_AS, (1200*1024*1024,)*2)
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
DEV, VID, HOST = "192.168.1.5:40579", "dQw4w9WgXcQ", "https://www.youtube.com"
OUT = "/home/iqbal/Documents/flufclip/docs/evidence/ask-ai-mode-2026-11-02"; os.makedirs(OUT, exist_ok=True)
def adb(*a): return subprocess.run(["adb","-s",DEV,*a],capture_output=True,timeout=30).stdout
raw = adb("exec-out","run-as","id.flufclip.player","cat","shared_prefs/youtube_auth.xml")
ck = next((e.text or "" for e in ET.fromstring(raw) if e.get("name")=="raw_cookies"), "")
loc = (adb("shell","getprop","persist.sys.locale").decode().strip() or adb("shell","getprop","ro.product.locale").decode().strip() or "en-US").split(",")[0]
lang, country = (loc.split("-")+["US"])[:2]; country = country if len(country)==2 else "US"
print("device locale (FlufPlay uses Locale.getDefault):", lang, country)
def cookie(n):
    m = re.search(r'(?:^|;\s*)%s=([^;]+)' % re.escape(n), ck); return m.group(1) if m else None
def req(url, body=None, headers=None):
    r = urllib.request.Request(url, data=(json.dumps(body).encode() if body is not None else None), headers=headers or {}, method="POST" if body is not None else "GET")
    try:
        x = urllib.request.urlopen(r, timeout=90); return x.status, x.read(6_000_000).decode("utf-8","replace")
    except urllib.error.HTTPError as e: return e.code, e.read(4000).decode("utf-8","replace")
def auth_headers(version, l, c):
    sap = cookie("SAPISID") or cookie("__Secure-3PAPISID"); ts = int(time.time())
    return {"User-Agent":UA,"Accept-Language":f"{l}-{c},{l};q=0.9,en;q=0.8","Content-Type":"application/json","X-Youtube-Client-Name":"1","X-Youtube-Client-Version":version,
            "Accept-Encoding":"identity","Cookie":ck,"Origin":HOST,"X-Origin":HOST,"X-Goog-AuthUser":"0",
            "Authorization":f"SAPISIDHASH {ts}_{hashlib.sha1(f'{ts} {sap} {HOST}'.encode()).hexdigest()}"}
def minimal_ctx(version, l, c): return {"client":{"clientName":"WEB","clientVersion":version,"userAgent":UA,"hl":l,"gl":c}}
# FlufPlay.getVersion(): scrape the version from the home page
_, home = req(f"{HOST}?hl={lang}&gl={country}", None, {"User-Agent":UA})
version = re.search(r'"INNERTUBE_CONTEXT_CLIENT_VERSION":"([^"]+)"', home).group(1)
def walk(o, path=()):
    yield path, o
    if isinstance(o, dict):
        for k,v in o.items(): yield from walk(v, path+(k,))
    elif isinstance(o, list):
        for i,v in enumerate(o): yield from walk(v, path+(f"[{i}]",))
def step1(l, c):
    st, txt = req(f"{HOST}/youtubei/v1/next?prettyPrint=false", {"context":minimal_ctx(version,l,c),"videoId":VID,"contentCheckOk":True,"racyCheckOk":True}, auth_headers(version,l,c))
    out = {"status":st,"bytes":len(txt)}
    if st!=200: out["error"]=txt[:200]; return out, None
    j = json.loads(txt)
    out["loggedOut"] = j.get("responseContext",{}).get("mainAppWebResponseContext",{}).get("loggedOut")
    cmds, composers, spark, panels = [], [], [], []
    for p,o in walk(j):
        if p and p[-1]=="sendUserQueryCommand": cmds.append((p,o))
        if p and p[-1]=="inputComposerViewModel": composers.append(p)
        if isinstance(o,dict):
            for k in ("iconType","iconName"):
                if o.get(k)=="SPARK": spark.append("/".join(p))
        if p and p[-1]=="engagementPanel" and "PAyouchat" in json.dumps(o)[:3000]: panels.append(p)
    out.update(sendUserQueryCommand=len(cmds), inputComposerViewModel=len(composers), sparkIcons=len(spark), youchatPanels=len(panels),
               sparkPaths=[s[-150:] for s in spark[:3]], hasApiUrl=bool(cmds and cmds[0][1]["innertubeCommand"].get("commandMetadata")))
    return out, (j, cmds[0][1]["innertubeCommand"] if cmds else None)
print("\n=== STEP 1: FlufPlay-style `next` (minimal context, no API key)")
results, chosen = {}, None
for l,c in dict.fromkeys([(lang,country),("en","US")]):
    o, payload = step1(l,c); results[f"{l}-{c}"]=o; print(f"  {l}-{c}:", json.dumps(o, ensure_ascii=False))
    if payload and payload[1] and chosen is None: chosen = (l,c,payload)
if not chosen: print("\nNo Ask command in any next response -> stop (nothing sent)."); raise SystemExit
l,c,(nextjson, ic) = chosen
DENY = re.compile(r'(?i)token|params|tracking|csn|visitordata|datasyncid|responseid|entitykey|identifier|clientmessageid|previousclientmessageid|clicktracking|^key$|signature|^id$')
def redact(o, k=""):
    if isinstance(o, dict): return {kk:(("<redacted>" if isinstance(vv,(str,int)) and DENY.search(kk) else redact(vv,kk))) for kk,vv in o.items()}
    if isinstance(o, list): return [redact(x,k) for x in o]
    if isinstance(o, str) and len(o)>=80 and re.fullmatch(r'[A-Za-z0-9_\-=%.:/]+', o): return f"<opaque {len(o)} chars>"
    return o
panel = next((o for p,o in walk(nextjson) if p and p[-1]=="engagementPanel" and "PAyouchat" in json.dumps(o)[:3000]), None)
json.dump({"locale":f"{l}-{c}","sendUserQueryCommand":redact(ic),"engagementPanel":redact(panel)}, open(f"{OUT}/next_ask_panel_redacted.json","w"), ensure_ascii=False, indent=1)
print("saved next_ask_panel_redacted.json")
print("\n=== STEP 2: send ONE question via get_panel")
q = "Ringkas video ini dalam satu kalimat." if l=="id" else "Summarize this video in one sentence."
token = ic["continuationCommand"]["token"]; path = (ic.get("commandMetadata",{}).get("webCommandMetadata",{}) or {}).get("apiUrl") or "/youtubei/v1/get_panel"
fd = {"inputComposerFormData":{"clientMessageId":f"youchat-{int(time.time()*1000)}","playerOffsetMs":"0","userInputText":q}}
variant = "minimal-context, no API key (FlufPlay style)"
st, txt = req(f"{HOST}{path}?prettyPrint=false", {"context":minimal_ctx(version,l,c),"continuation":token,"formData":fd}, auth_headers(version,l,c))
print(f"  attempt 1 [{variant}] -> HTTP {st}, {len(txt)} bytes")
if st!=200:
    print("  error body:", txt[:240].replace("\n"," "))
    _, html = req(f"{HOST}/watch?v={VID}&hl={l}&gl={c}", None, {"User-Agent":UA,"Cookie":ck})
    key = re.search(r'"INNERTUBE_API_KEY":"([^"]+)"', html).group(1); i = html.index('"INNERTUBE_CONTEXT":')+len('"INNERTUBE_CONTEXT":'); ctx,_ = json.JSONDecoder().raw_decode(html[i:])
    h = auth_headers(version,l,c); h.update({"Referer":f"{HOST}/watch?v={VID}","X-Goog-Visitor-Id":ctx["client"].get("visitorData",""),"X-Youtube-Bootstrap-Logged-In":"true"})
    fd["inputComposerFormData"]["clientMessageId"]=f"youchat-{int(time.time()*1000)}"; variant="full web context + API key"
    st, txt = req(f"{HOST}{path}?key={key}&prettyPrint=false", {"context":ctx,"continuation":token,"formData":fd}, h)
    print(f"  attempt 2 [{variant}] -> HTTP {st}, {len(txt)} bytes")
if st==200:
    j = json.loads(txt)
    json.dump(redact(j), open(f"{OUT}/get_panel_response_redacted.json","w"), ensure_ascii=False, indent=1)
    print("  saved get_panel_response_redacted.json | top-level keys:", list(j))
    print("  text strings under `content` (path -> text):")
    for p,o in walk(j):
        if p and p[-1]=="content" and isinstance(o,str): print("   ", "/".join(p)[-110:], "->", o[:150].replace("\n"," "))
json.dump({"step1":results,"question_language":l,"route":path,"variant_ok":variant if st==200 else None,"http":st,"headers_sent":["Cookie","Authorization(SAPISIDHASH)","Origin","X-Origin","X-Goog-AuthUser","X-Youtube-Client-Name","X-Youtube-Client-Version","Content-Type","Accept-Language","User-Agent"]}, open(f"{OUT}/probe_summary.json","w"), indent=1)
