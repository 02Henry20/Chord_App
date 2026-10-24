"""Decode the app's private DataStore scalar map into its versioned logical backup preferences."""
import argparse,json,struct
from pathlib import Path

def varint(data,i):
    value=shift=0
    while True:
        byte=data[i]; i+=1; value|=(byte&127)<<shift
        if byte<128: return value,i
        shift+=7
def fields(data):
    i=0
    while i<len(data):
        tag,i=varint(data,i); wire=tag&7
        if wire==0: value,i=varint(data,i)
        elif wire==2: length,i=varint(data,i); value=data[i:i+length]; i+=length
        elif wire in (1,5): size=8 if wire==1 else 4; value=struct.unpack('<d' if wire==1 else '<f',data[i:i+size])[0]; i+=size
        else: raise ValueError('Unsupported preference wire type')
        yield tag>>3,wire,value
def decode(path):
    result={}
    for number,wire,entry in fields(Path(path).read_bytes()):
        if number!=1 or wire!=2: continue
        parts={n:v for n,w,v in fields(entry)}; key=parts[1].decode('utf-8')
        value_fields=list(fields(parts[2])); _,wire,value=value_fields[0]
        result[key]=value.decode('utf-8') if wire==2 else value
    return result
def logical(values):
    return dict(version=1,theme=values.get('theme','DARK' if values.get('dark',0) else 'LIGHT'),instrument=values.get('instrument','Piano'),fontSize=values.get('fontSize',19),keepAwake=bool(values.get('keepAwake',1)),
                chordLabels=values.get('chordLabels','ROMAN' if values.get('roman',0) else 'SYMBOLS'),spelling=values.get('spelling','AUTO'),scrollSpeed=values.get('scrollSpeed',22.0),leadIn=values.get('leadIn',0),lineSpacing=values.get('lineSpacing',1.35),chordColor=values.get('chordColor','Theme'),sheetDisplay=values.get('sheetDisplay','BOTH'),
                sheetMargin=values.get('sheetMargin',16.0),songLineGap=values.get('songLineGap',6.0),chordLineGap=values.get('chordLineGap',2.0),sectionGap=values.get('sectionGap',12.0))
if __name__=='__main__':
    p=argparse.ArgumentParser(); p.add_argument('source',type=Path); p.add_argument('destination',type=Path); a=p.parse_args()
    a.destination.write_text(json.dumps(logical(decode(a.source)),indent=2),encoding='utf-8')
