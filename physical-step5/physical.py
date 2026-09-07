#!/usr/bin/env python3
"""Run by the tester only after software certification; never declares visual acceptance."""
import argparse, datetime, hashlib, json, pathlib, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parent
APK_NAMES = ('VideoFlow_Step5_Review.apk', 'VideoFlow_Step5_Debug.apk', 'VideoFlow_Step5_Debug-androidTest.apk')

def adb(*args, output=None, check=True):
    command=['adb']+list(args)
    if output:
        with output.open('wb') as stream:
            return subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, check=check)
    return subprocess.run(command, check=check, capture_output=True, text=True)

def verify():
    identity=json.loads((ROOT/'ARTIFACT_IDENTITY.json').read_text())
    if identity['automatedStatus'] != 'PASS':
        raise RuntimeError('This package has not passed exact-head software certification.')
    rows={line.split()[1].lstrip('*'):line.split()[0] for line in (ROOT/'SHA256SUMS.txt').read_text().splitlines() if line.strip()}
    for name in APK_NAMES:
        digest=hashlib.sha256()
        with (ROOT/name).open('rb') as stream:
            for block in iter(lambda:stream.read(1024*1024),b''): digest.update(block)
        if digest.hexdigest()!=rows.get(name): raise RuntimeError('APK checksum mismatch: '+name)
    return identity

def capture(folder):
    folder.mkdir(parents=True,exist_ok=True)
    for name,args in {'device':['shell','getprop'], 'memory':['shell','dumpsys','meminfo','com.videoflow.app.debug'],
        'thermal':['shell','dumpsys','thermalservice'], 'battery':['shell','dumpsys','battery'],
        'storage':['shell','df','-k'], 'logcat':['logcat','-d','-v','threadtime']}.items():
        adb(*args,output=folder/(name+'.txt'),check=False)
    adb('pull','/sdcard/Android/data/com.videoflow.app.debug/files/ai-endurance',str(folder/'ai-endurance'),check=False)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['verify','install','capture','endurance'])
    parser.add_argument('--source-uri',help='Exact original content URI already readable by the Debug app after SAF import')
    parser.add_argument('--start-us',type=int,default=0)
    parser.add_argument('--end-us',type=int,default=1_800_000_000)
    parser.add_argument('--roi',default='0.70,0.05,0.90,0.15')
    parser.add_argument('--checkpoint-us',type=int,default=60_000_000)
    args=parser.parse_args()
    identity=verify()
    if args.action=='verify': print('Exact-head package hashes verified:',identity['commit']);return
    if adb('get-state').stdout.strip()!='device': raise RuntimeError('Connect one authorized Android device first.')
    if args.action=='install':
        for name in APK_NAMES: print(adb('install','-r',str(ROOT/name)).stdout.strip())
        print('Review is ready for manual testing. Debug + androidTest is ready for instrumentation.');return
    folder=ROOT/'results'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    folder.mkdir(parents=True,exist_ok=False)
    (folder/'identity.json').write_text(json.dumps(identity,indent=2))
    if args.action=='capture': capture(folder);print(folder);return
    if not args.source_uri or not args.source_uri.startswith('content://'): parser.error('--source-uri must be the readable original content URI')
    if not 0<=args.start_us<args.end_us or args.checkpoint_us<=0: parser.error('Invalid Long time range')
    roi=[float(x) for x in args.roi.split(',')]
    if len(roi)!=4 or not 0<=roi[0]<roi[2]<=1 or not 0<=roi[1]<roi[3]<=1: parser.error('ROI must be left,top,right,bottom normalized to 0..1')
    capture(folder/'before')
    result=None
    try:
        result=adb('shell','am','instrument','-w','-r','-e','class',
            'com.videoflow.app.ai.LongAiEnduranceInstrumentedTest','-e','vfSourceUri',args.source_uri,
            '-e','vfStartUs',str(args.start_us),'-e','vfEndUs',str(args.end_us),'-e','vfRoi',args.roi,
            '-e','vfCheckpointUs',str(args.checkpoint_us),'-e','vfKeepOutput','true',
            'com.videoflow.app.debug.test/androidx.test.runner.AndroidJUnitRunner',output=folder/'endurance.txt',check=False)
    finally: capture(folder/'after')
    log=(folder/'endurance.txt').read_text(errors='replace')
    passed=result.returncode==0 and 'LONG_AI_ENDURANCE_EXPORT_CERTIFIED' in log and 'OK (1 test)' in log and not any(x in log for x in ('FAILURES!!!','INSTRUMENTATION_FAILED','Process crashed','shortMsg='))
    (folder/'result.json').write_text(json.dumps({'harness':'PASS' if passed else 'FAIL','physicalVisualAcceptance':'NOT VERIFIED',
        'startUs':args.start_us,'endUs':args.end_us,'sourceUri':args.source_uri,'roi':roi},indent=2))
    print('Harness:', 'PASS' if passed else 'FAIL', '| Visual/A/V/quality acceptance: NOT VERIFIED |',folder)
    if not passed: sys.exit(1)

if __name__=='__main__': main()
