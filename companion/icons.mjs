import {validateIcons} from '../tools/design.mjs';

// Android vectors consume the same restricted geometry as the TV/browser icon catalog.
export function vectorIcon(svg,mirrored=false) {
  validateIcons({icon:svg});
  const paths=[];
  for(const [,kind,source] of svg.matchAll(/<(path|circle|ellipse|rect)((?: [a-z]+="[^"<>]*")*)\/>/g)){
    const a=Object.fromEntries([...source.matchAll(/ ([a-z]+)="([^"]*)"/g)].map(([,key,value])=>[key,value]));
    const n=key=>Number(a[key]||0);let d=a.d;
    if(kind==='circle'||kind==='ellipse'){
      const x=n('cx'),y=n('cy'),rx=kind==='circle'?n('r'):n('rx'),ry=kind==='circle'?rx:n('ry');
      d=`M${x-rx} ${y}A${rx} ${ry} 0 1 0 ${x+rx} ${y}A${rx} ${ry} 0 1 0 ${x-rx} ${y}Z`;
    }else if(kind==='rect'){
      const x=n('x'),y=n('y'),w=n('width'),h=n('height'),rx=Math.min(n('rx')||n('ry'),w/2),ry=Math.min(n('ry')||n('rx'),h/2);
      d=rx&&ry?`M${x+rx} ${y}H${x+w-rx}A${rx} ${ry} 0 0 1 ${x+w} ${y+ry}V${y+h-ry}A${rx} ${ry} 0 0 1 ${x+w-rx} ${y+h}H${x+rx}A${rx} ${ry} 0 0 1 ${x} ${y+h-ry}V${y+ry}A${rx} ${ry} 0 0 1 ${x+rx} ${y}Z`:`M${x} ${y}h${w}v${h}h${-w}Z`;
    }
    paths.push(`<path android:pathData="${d}" android:fillColor="${a.fill==='currentColor'?'#FFFFFFFF':'#00000000'}" android:strokeColor="${a.stroke==='none'?'#00000000':'#FFFFFFFF'}" android:strokeWidth="1.8" android:strokeLineCap="round" android:strokeLineJoin="round"/>`);
  }
  return `<!-- Generated from shared/design/icons.json. Do not edit. -->\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24" android:autoMirrored="${mirrored}">${paths.join('')}</vector>\n`;
}
