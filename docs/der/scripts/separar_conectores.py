import re,sys,collections
SRC,OUT=sys.argv[1],sys.argv[2]
GAP=float(sys.argv[3]) if len(sys.argv)>3 else 7.0
s=open(SRC,encoding='utf-8').read()
num=r'-?\d+(?:\.\d+)?'
def pts(txt): return [(float(a),float(b)) for a,b in re.findall(rf'({num}),({num})',txt)]
class E: pass
NB=[]
for m in re.finditer(r'<g id="node\d+" class="node">(.*?)</g>',s,re.S):
    Q=[p for t in re.findall(r'points="([^"]+)"',m.group(1)) for p in pts(t)]
    if Q: NB.append((min(x for x,y in Q),min(y for x,y in Q),max(x for x,y in Q),max(y for x,y in Q)))
def node_at(p,dr):
    for b in NB:
        if dr=='H' and (abs(p[0]-b[0])<16 or abs(p[0]-b[2])<16) and b[1]-2<=p[1]<=b[3]+2: return b,'x'
        if dr=='V' and (abs(p[1]-b[1])<16 or abs(p[1]-b[3])<16) and b[0]-2<=p[0]<=b[2]+2: return b,'y'
    return None,None

edges=[]
for m in re.finditer(r'(<g id="edge\d+" class="edge">)(.*?)(</g>)',s,re.S):
    e=E(); e.span=m.span(2); e.body=m.group(2)
    dm=re.search(r' d="([^"]+)"',e.body)
    P=pts(dm.group(1)); V=[P[0]]+P[3::3]
    # drop zero-length
    W=[V[0]]
    for p in V[1:]:
        if abs(p[0]-W[-1][0])>1e-6 or abs(p[1]-W[-1][1])>1e-6: W.append(p)
    e.V=[list(p) for p in W]
    e.ortho=all(abs(a[0]-b[0])<0.01 or abs(a[1]-b[1])<0.01 for a,b in zip(W,W[1:]))
    e.shift={0:(0,0),1:(0,0)}  # translation for arrow elems at start/end
    edges.append(e)
def seg_dir(a,b): return 'H' if abs(a[1]-b[1])<0.01 else 'V'
MIN=GAP
segs=[]
for i,e in enumerate(edges):
    if not e.ortho: continue
    V=e.V
    for j in range(len(V)-1):
        a,b=V[j],V[j+1]; dr=seg_dir(a,b)
        c=a[0] if dr=='V' else a[1]; lo,hi=sorted((a[1],b[1]) if dr=='V' else (a[0],b[0]))
        segs.append(dict(i=i,j=j,dr=dr,c=c,lo=lo,hi=hi,term=(j==0 or j==len(V)-2)))
for dr in 'HV':
    S=sorted([g for g in segs if g['dr']==dr],key=lambda g:g['c'])
    # build clusters: overlapping extents and close coords (transitive)
    n=len(S); parent=list(range(n))
    def f(x):
        while parent[x]!=x: parent[x]=parent[parent[x]]; x=parent[x]
        return x
    for a in range(n):
        for b in range(a+1,n):
            if S[b]['c']-S[a]['c']>=MIN: break
            if S[a]['i']==S[b]['i']: continue
            if min(S[a]['hi'],S[b]['hi'])-max(S[a]['lo'],S[b]['lo'])>3: parent[f(a)]=f(b)
    cl=collections.defaultdict(list)
    for a in range(n): cl[f(a)].append(S[a])
    for grp in cl.values():
        if len(grp)<2: continue
        grp.sort(key=lambda g:(g['c'],g['lo']))
        m=len(grp); center=sum(g['c'] for g in grp)/m; gap=MIN
        # terminal endpoints on a node side: keep within that side
        rng=None
        for g in grp:
            V=edges[g['i']].V
            for idx in ([0] if g['j']==0 else [])+([len(V)-1] if g['j']+1==len(V)-1 else []):
                b,_=node_at(V[idx],dr)
                if b:
                    lo,hi=(b[1]+5,b[3]-5) if dr=='H' else (b[0]+5,b[2]-5)
                    rng=(lo,hi) if rng is None else (max(rng[0],lo),min(rng[1],hi))
        if rng and rng[1]>rng[0]:
            gap=min(MIN,(rng[1]-rng[0])/max(1,m-1)) if m>1 else MIN
            half=gap*(m-1)/2
            center=min(max(center,rng[0]+half),rng[1]-half)
        for k,g in enumerate(grp):
            target=center+(k-(m-1)/2)*gap; d=target-g['c']
            if abs(d)<0.01: continue
            e=edges[g['i']]; V=e.V; j=g['j']
            if dr=='V': V[j][0]+=d; V[j+1][0]+=d; t=(d,0)
            else: V[j][1]+=d; V[j+1][1]+=d; t=(0,d)
            if j==0: e.shift[0]=(e.shift[0][0]+t[0],e.shift[0][1]+t[1])
            if j+1==len(V)-1: e.shift[1]=(e.shift[1][0]+t[0],e.shift[1][1]+t[1])
# rebuild
out=[];last=0
for e in edges:
    body=e.body
    V=e.V
    d='M%.2f,%.2f'%tuple(V[0])+'C'+' '.join('%.2f,%.2f %.2f,%.2f %.2f,%.2f'%(a[0],a[1],b[0],b[1],b[0],b[1]) for a,b in zip(V,V[1:]))
    body=re.sub(r' d="[^"]+"',' d="%s"'%d,body,count=1)
    # arrow elements: translate those near original start/end
    dm=re.search(r' d="([^"]+)"',e.body); P=pts(dm.group(1)); p0,p1=P[0],P[-1]
    def fix(m):
        tag=m.group(0)
        if tag.startswith('<path'): return tag
        if tag.startswith('<ellipse'):
            cx=float(re.search(r'cx="([^"]+)"',tag).group(1)); cy=float(re.search(r'cy="([^"]+)"',tag).group(1))
            Q=[(cx,cy)]
        else:
            Q=pts(re.search(r'points="([^"]+)"',tag).group(1))
        d0=max(abs(x-p0[0])+abs(y-p0[1]) for x,y in Q); d1=max(abs(x-p1[0])+abs(y-p1[1]) for x,y in Q)
        end=0 if d0<d1 else 1; tx,ty=e.shift[end]
        if tx==0 and ty==0: return tag
        if tag.startswith('<ellipse'):
            tag=re.sub(r'cx="([^"]+)"',lambda q:'cx="%.2f"'%(float(q.group(1))+tx),tag)
            return re.sub(r'cy="([^"]+)"',lambda q:'cy="%.2f"'%(float(q.group(1))+ty),tag)
        return re.sub(r'points="([^"]+)"',lambda q:'points="'+' '.join('%.2f,%.2f'%(x+tx,y+ty) for x,y in pts(q.group(1)))+' "',tag)
    body=re.sub(r'<(?:polygon|polyline|ellipse|path)[^>]*>',fix,body)
    out.append(s[last:e.span[0]]); out.append(body); last=e.span[1]
out.append(s[last:])
open(OUT,'w',encoding='utf-8').write(''.join(out))
print(OUT,'edges',len(edges),'non-ortho',sum(not e.ortho for e in edges))
