#!/usr/bin/env python3
"""Verify corrections in the actual packaged Debug and Review DEX, not just source text."""
import json, os, pathlib, re, subprocess, tempfile, zipfile

TARGETS = {
    'Landroidx/media3/effect/FinalShaderProgramWrapper;',
    'Landroidx/media3/effect/DefaultVideoCompositor;',
    'Landroidx/media3/effect/MultipleInputVideoGraph$SingleContextGlObjectsProvider;',
    'Lcom/videoflow/app/render/SafMediaMuxerFactory;',
}
def method(code, name):
    lines=code.splitlines(); result=[]; active=False
    for line in lines:
        match=re.match(r"\s*name\s*:\s*'([^']+)'",line)
        if match:
            if active: break
            active=match.group(1)==name
        if active: result.append(line)
    assert result, name
    return '\n'.join(result)

results={}
dexdump=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0/dexdump'
for variant in ('debug','review'):
    apk=pathlib.Path(f'app/build/outputs/apk/{variant}/app-{variant}.apk')
    found={}
    with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory() as folder:
        for name in archive.namelist():
            if not re.fullmatch(r'classes\d*\.dex',name): continue
            dex=pathlib.Path(folder)/name
            with archive.open(name) as src, dex.open('wb') as dst:
                import shutil
                shutil.copyfileobj(src,dst)
            process=subprocess.Popen([str(dexdump),'-d',str(dex)],stdout=subprocess.PIPE,text=True,errors='replace')
            current=None; lines=[]
            for line in process.stdout:
                match=re.search(r"Class descriptor\s*:\s*'([^']+)'",line)
                if match:
                    if current in TARGETS: found[current]=''.join(lines)
                    current=match.group(1); lines=[]
                if current in TARGETS: lines.append(line)
            if current in TARGETS: found[current]=''.join(lines)
            assert process.wait()==0
            dex.unlink()
    assert set(found)==TARGETS, (variant,sorted(found))
    wrapper=method(found['Landroidx/media3/effect/FinalShaderProgramWrapper;'],'release')
    assert len(re.findall(r'GlUtil.*destroyEglSurface',wrapper))==2, wrapper
    assert 'placeholderSurface' in wrapper, wrapper
    compositor=method(found['Landroidx/media3/effect/DefaultVideoCompositor;'],'getFramesToComposite')
    assert re.search(r'CausalFrameSelection.*distanceUs',compositor), compositor
    provider=method(found['Landroidx/media3/effect/MultipleInputVideoGraph$SingleContextGlObjectsProvider;'],'release')
    assert re.search(r'EGL14.*eglReleaseThread',provider), provider
    muxer=found['Lcom/videoflow/app/render/SafMediaMuxerFactory;']
    assert 'Mp4Muxer$Builder' in muxer and 'AutoCloseOutputStream' in muxer, muxer
    results[variant]={'ownedPlaceholderSurfaceRelease':True,'causalFrameSelection':True,'eglThreadRelease':True,'directSafMp4Muxer':True}
    print('STEP5_PACKAGED_MEDIA3_VERIFIED',variant,json.dumps(results[variant]))
pathlib.Path('step5-bytecode-evidence.json').write_text(json.dumps({'commit':os.environ['GITHUB_SHA'],'variants':results},indent=2))
