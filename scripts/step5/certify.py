#!/usr/bin/env python3
"""Fail-closed certification from actual exact-run build/runtime evidence."""
import argparse, hashlib, json, os, pathlib, re, shutil, xml.etree.ElementTree as ET, zipfile

ROOT=pathlib.Path('.')
def unit_results(folder):
    files=list(pathlib.Path(folder).rglob('TEST-*.xml'))
    assert files, 'JUnit XML is missing'
    results={}
    for file in files:
        suite=ET.parse(file).getroot()
        assert int(suite.get('failures',0))==0 and int(suite.get('errors',0))==0 and int(suite.get('skipped',0))==0, file
        key=file.parent.name
        results.setdefault(key,{})[suite.get('name')]=int(suite.get('tests',0))
    return results

def architecture(folder):
    results=unit_results(folder)
    for variant,suites in results.items():
        assert suites.get('com.videoflow.app.domain.ai.Step5ArchitectureTest')==6, (variant,suites.keys())
        assert suites.get('com.videoflow.app.domain.ai.Step5CheckpointIdentityTest')==2, variant
        assert sum(suites.values())>=140, (variant,sum(suites.values()))
    pathlib.Path('long-media-evidence.json').write_text(json.dumps({'commit':os.environ['GITHUB_SHA'],'variants':results},indent=2))

def packaging(folder):
    root=pathlib.Path(folder)
    assert (root/'SOURCE_COMMIT.txt').read_text().strip()==os.environ['GITHUB_SHA']
    hashes={line.split()[1]:line.split()[0] for line in (root/'SHA256SUMS.txt').read_text().splitlines()}
    expected={'assets/models/lama-512-int8.onnx':('cab19978adc306622fe37ef60d4a52103b99c98141d499c2a2366a7ed1255dbe',62074990),
        'assets/models/lama-dynamic-int8.onnx':('1941214c210399eb815eb2d32570ba91d5e6c4ac3de4c939bd3fb09300454972',61512617)}
    for name in ('VideoFlow_Step5_Debug.apk','VideoFlow_Step5_Review.apk','VideoFlow_Step5_Debug-androidTest.apk'):
        assert hashes[name]==hashlib.file_digest((root/name).open('rb'),'sha256').hexdigest(), name
        with zipfile.ZipFile(root/name) as apk:
            if 'androidTest' in name:
                for asset in ('sample_av.mp4','step5-colour.mp4','step5-sync.mp4','step5-vfr.mp4','step5-44100.mp4'): assert apk.getinfo('assets/'+asset).file_size>1024
            else:
                for asset,(sha,size) in expected.items():
                    assert apk.getinfo(asset).file_size==size
                    with apk.open(asset) as stream: assert hashlib.file_digest(stream,'sha256').hexdigest()==sha
    permissions=(root/'review-permissions.txt').read_text()
    assert 'android.permission.INTERNET' not in permissions and 'android.permission.ACCESS_NETWORK_STATE' not in permissions
    assert 'f3d4e66b350800bca739b2c5f6f4d2c7f15c7dc89b1b8763bd51468ab7150cc7' in (root/'review-signature.txt').read_text().lower()
    pathlib.Path('packaging-evidence.json').write_text(json.dumps({'commit':os.environ['GITHUB_SHA'],'apkSha256':hashes,'models':expected},indent=2))

def report():
    needs=json.loads(os.environ['CERTIFICATION_NEEDS'])
    assert needs and all(v['result']=='success' for v in needs.values()), needs
    packaging('verified-apks')
    units=unit_results('verified-build')
    logs=pathlib.Path('verified-runtime')
    names=['step5-integration','step5-layout-portrait','step5-layout-landscape','step5-layout-tablet','step5-process-start','step5-process-recovery',
        'product-panels','ai-runtime','final-ai-export','editor-regression','professional-upgrade','retained-product-regression']
    counts={}
    for name in names:
        text=(logs/(name+'.txt')).read_text(errors='replace')
        assert not re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=',text),name
        matches=re.findall(r'OK \((\d+) tests?\)',text)
        assert len(matches)==1,name
        counts[name]=int(matches[0])
    assert counts['step5-integration']==10 and counts['professional-upgrade']==5
    for name in ('quality.jsonl','av-sync.jsonl','ai-roi-quality.jsonl','resources.jsonl','vfr-cadence.jsonl'):
        path=logs/'step5-evidence'/name
        rows=[json.loads(line) for line in path.read_text().splitlines()]
        assert rows,name
    commit=os.environ['GITHUB_SHA'];run=os.environ['GITHUB_RUN_ID'];repo=os.environ['GITHUB_REPOSITORY']
    identity={'repository':repo,'branch':os.environ['GITHUB_REF_NAME'],'commit':commit,'workflowRunId':run,
        'workflowUrl':f'https://github.com/{repo}/actions/runs/{run}','automatedStatus':'PASS','physicalStatus':'NOT VERIFIED',
        'buildTypes':['debug','review','debugAndroidTest'],'modelVersions':['lama-512-int8-v1','lama-dynamic-int8-v1']}
    target=pathlib.Path('completion-report');target.mkdir()
    package=pathlib.Path('physical-package');shutil.copytree('physical-step5',package)
    tokens={'{{COMMIT}}':commit,'{{RUN_ID}}':run,'{{RUN_URL}}':identity['workflowUrl'],'{{AUTOMATED_STATUS}}':'PASS'}
    for source in list(ROOT.glob('STEP_5_*.md'))+[ROOT/'RELEASE_CANDIDATE_MANIFEST.md']:
        text=source.read_text()
        for token,value in tokens.items():text=text.replace(token,value)
        (target/source.name).write_text(text)
        (package/source.name).write_text(text)
    for dest in (target,package):
        (dest/'ARTIFACT_IDENTITY.json').write_text(json.dumps(identity,indent=2))
        shutil.copy('verified-apks/SHA256SUMS.txt',dest/'SHA256SUMS.txt')
        shutil.copy('verified-apks/SOURCE_COMMIT.txt',dest/'SOURCE_COMMIT.txt')
        (dest/'certification-results.json').write_text(json.dumps({'units':{k:sum(v.values()) for k,v in units.items()},'instrumentation':counts,'totalInstrumentedExecutions':sum(counts.values()),'physical':'NOT VERIFIED'},indent=2))
    shutil.copytree(logs/'step5-evidence',target/'measurements')
    for apk in pathlib.Path('verified-apks').glob('*.apk'):shutil.copy(apk,package/apk.name)
    (package/'results').mkdir()
    (package/'results'/'README.md').write_text('No physical tests have been run. Each physical.py invocation creates a separate UTC-stamped evidence directory.\n')
    shutil.make_archive('VideoFlow_Step5_Physical_Test_Package','zip',package)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['architecture','packaging','report']);parser.add_argument('folder',nargs='?');args=parser.parse_args()
    if args.mode=='architecture':architecture(args.folder)
    elif args.mode=='packaging':packaging(args.folder)
    else:report()
