/* Interactive design fixtures. Production calculations and device access stay native. */
const reviewToday = new Date('2026-11-21T12:00:00Z');
const dayMillis = 86400000;
const hijri = new Intl.DateTimeFormat('en-u-ca-islamic-umalqura', {day:'numeric',month:'long',year:'numeric',timeZone:'Africa/Cairo'});
const gregorian = new Intl.DateTimeFormat('en', {weekday:'long',day:'numeric',month:'long',year:'numeric',timeZone:'Africa/Cairo'});
let calendarAnchor = new Date(reviewToday), calendarSelected = new Date(reviewToday);
function hijriParts(date) {return Object.fromEntries(hijri.formatToParts(date).map(p=>[p.type,p.value]));}
function monthDays(anchor) {
  let first=new Date(anchor);
  while(+hijriParts(first).day!==1) first=new Date(+first-dayMillis);
  const month=hijriParts(first).month, days=[];
  for(let date=new Date(first);hijriParts(date).month===month;date=new Date(+date+dayMillis)) days.push(date);
  return days;
}
function calendarMarkup() {
  const dates=monthDays(calendarAnchor), parts=hijriParts(dates[0]), leading=(dates[0].getUTCDay()+1)%7;
  return `<div class="month-heading"><button class="iconbtn" aria-label="Previous Hijri month" data-month="-1">${icon('back')}</button><div><strong>${parts.month}</strong><small>${parts.year} AH</small></div><button class="iconbtn" aria-label="Next Hijri month" data-month="1"><span class="chev">${icon('back')}</span></button></div><div class="month-grid" role="group" aria-label="${parts.month} ${parts.year}">${['Sat','Sun','Mon','Tue','Wed','Thu','Fri'].map(d=>`<span class="weekday">${d}</span>`).join('')}${'<span></span>'.repeat(leading)}${dates.map(date=>`<button data-date="${date.toISOString()}" aria-label="${hijri.format(date)}" aria-pressed="${+date===+calendarSelected}" ${+date===+reviewToday?'aria-current="date"':''}>${hijriParts(date).day}</button>`).join('')}</div>`;
}
// shortcut: times are illustrative daily shifts; replace with the native engine before implementation.
function fixturePrayerTimes(date) {
  const shift=Math.round((+date-+reviewToday)/dayMillis);
  return ['04:51','11:41','14:37','16:58','18:17'].map(time=>{
    const [h,m]=time.split(':').map(Number), value=((h*60+m+shift)%1440+1440)%1440;
    return String(Math.floor(value/60)).padStart(2,'0')+':'+String(value%60).padStart(2,'0');
  });
}
function selectedDayMarkup() {
  const schedule=fixturePrayerTimes(calendarSelected);
  return `<div class="day-heading"><button class="iconbtn" aria-label="Previous prayer day" data-day="-1">${icon('back')}</button><div><h2>${hijri.format(calendarSelected)}</h2><p class="sub">${gregorian.format(calendarSelected)}</p></div><button class="iconbtn" aria-label="Next prayer day" data-day="1"><span class="chev">${icon('back')}</span></button></div>${group(prayerNames.map((n,i)=>row(n,schedule[i],'',n.toLowerCase())).join(''))}`;
}
function hydrateCalendars() {
  document.querySelectorAll('[data-calendar]').forEach(e=>e.innerHTML=calendarMarkup());
  document.querySelectorAll('[data-selected-day]').forEach(e=>e.innerHTML=selectedDayMarkup());
  hydrateIcons();
}
function durationAtPercent(duration,percent) {return duration*Math.max(5,Math.min(100,+percent))/100;}
function lengthControl(recording="almalki") {
  return `<div class="length-control" data-recording="${recording}"><div class="length-heading"><span>Adhan length</span><output data-length-value>100% · Full adhan</output></div><div class="length-line"><input type="range" class="range" min="5" max="100" step="5" value="100" aria-label="Adhan length"><button class="iconbtn" aria-label="Preview selected length" data-preview-ending>${icon('play')}</button></div><small data-cutoff-time>Ending uses the selected recording’s duration.</small></div>`;
}
let previewAudio=null, previewButton=null;
function stopEndingPreview() {
  if(previewAudio) {previewAudio.pause();previewAudio.removeAttribute('src');previewAudio.load();}
  if(previewButton) {previewButton.setAttribute('aria-label','Preview selected length');previewButton.innerHTML=icon('play');}
  previewAudio=null;previewButton=null;
}
function previewEnding(button) {
  if(previewButton===button) {stopEndingPreview();return;}
  stopEndingPreview();
  const control=button.closest('.length-control'),percent=+control.querySelector('input').value;
  const audio=new Audio(root+'shared/media/adhan/'+control.dataset.recording+'.mp3');
  previewAudio=audio;previewButton=button;
  audio.onloadedmetadata=()=>{
    if(previewAudio!==audio)return;
    const end=durationAtPercent(audio.duration,percent);
    audio.currentTime=0;
    control.querySelector('[data-cutoff-time]').textContent=`Ends at ${Math.floor(end/60)}:${String(Math.floor(end%60)).padStart(2,'0')} of the selected adhan`;
    button.setAttribute('aria-label','Stop preview');button.innerHTML=icon('stop');
    audio.ontimeupdate=()=>{if(audio.currentTime>=end)stopEndingPreview();};
    audio.play().catch(()=>{stopEndingPreview();toast('Preview could not start.');});
  };
  audio.onended=stopEndingPreview;
  audio.onerror=()=>{if(previewAudio===audio){stopEndingPreview();toast('Recording unavailable.');}};
}
function qiblaBearing(lat,lon) {
  const r=Math.PI/180, dest=21.4225*r, start=lat*r, delta=(39.8262-lon)*r;
  return (Math.atan2(Math.sin(delta)*Math.cos(dest),Math.cos(start)*Math.sin(dest)-Math.sin(start)*Math.cos(dest)*Math.cos(delta))/r+360)%360;
}
function projectedRoute() {
  const r=Math.PI/180, a=[30.0444*r,31.2357*r], b=[21.4225*r,39.8262*r];
  const v=([lat,lon])=>[Math.cos(lat)*Math.cos(lon),Math.cos(lat)*Math.sin(lon),Math.sin(lat)];
  const av=v(a), bv=v(b), angle=Math.acos(av.reduce((sum,x,i)=>sum+x*bv[i],0));
  return Array.from({length:25},(_,i)=>{
    const t=i/24, x=av.map((n,j)=>(Math.sin((1-t)*angle)*n+Math.sin(t*angle)*bv[j])/Math.sin(angle));
    const lat=Math.atan2(x[2],Math.hypot(x[0],x[1])), lon=Math.atan2(x[1],x[0]);
    return [(lon/Math.PI+1)/2*8192-18*256,(1-Math.log(Math.tan(lat)+1/Math.cos(lat))/Math.PI)/2*8192-13*256];
  });
}
function mapMarkup() {
  const points=projectedRoute(),first=points[0],last=points[points.length-1];
  return `<div class="qibla-map" aria-label="Cairo to the Kaaba on a north-up map"><div class="map-tiles">${[13,14].flatMap(y=>[18,19].map(x=>`<img data-map-src="https://tile.openstreetmap.org/5/${x}/${y}.png" alt="" width="256" height="256">`)).join('')}</div><svg class="route-overlay" viewBox="0 0 512 512" role="img" aria-label="Great-circle direction from Cairo to the Kaaba"><polyline points="${points.map(p=>p.join(',')).join(' ')}" fill="none" stroke="#fff" stroke-width="9"/><polyline points="${points.map(p=>p.join(',')).join(' ')}" fill="none" stroke="#0A6060" stroke-width="5"/><circle cx="${first[0]}" cy="${first[1]}" r="8" fill="#0A6060" stroke="white" stroke-width="3"/><circle cx="${last[0]}" cy="${last[1]}" r="8" fill="#172427" stroke="white" stroke-width="3"/><text x="${first[0]-18}" y="${first[1]-17}">Cairo</text><text x="${last[0]-50}" y="${last[1]+32}">Kaaba</text></svg><span class="north">N ↑</span><a class="map-credit" href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">© OpenStreetMap contributors</a></div>`;
}
function hydrateIcons() {document.querySelectorAll('svg[data-icon]').forEach(svg=>svg.innerHTML=icons[svg.dataset.icon]||'');}
function mediaControls(kind,label,glyph) {
  return `<div class="row media-row">${icon(glyph)}<span class="text">${label}</span><div class="media-controls" role="group" aria-label="${label}"><button class="iconbtn" aria-label="Play ${label}" data-media="${kind}" data-media-state="play">${icon('play')}</button><button class="iconbtn" aria-label="Stop ${label}" data-media="${kind}" data-media-state="stop">${icon('stop')}</button></div></div>`;
}

let overridePrayer='Dhuhr', overrideDay='Monday';
const dayOverrides={Dhuhr:Object.fromEntries(['Monday','Tuesday','Wednesday','Thursday','Friday'].map(d=>[d,{sound:'Silent',vibrate:false}]))};
function refreshOverrideRows() {
 for(const id of ['schedule','schedule-today']) {
  const board=document.getElementById(id);
  board.querySelector('.content h2').textContent=overridePrayer+(id==='schedule'?' · day overrides':' · this week');
  board.querySelectorAll('.content .group .row').forEach((row,i)=>{const value=dayOverrides[overridePrayer]?.[weekdays[i]];row.querySelector('.value').textContent=value?value.sound+(value.vibrate?' · Vibrate':''):id==='schedule'?'Use default':'Adhan';});
 }
 document.querySelectorAll('#schedule .foot [data-go],#schedule .appbar [data-go]').forEach(b=>b.dataset.go='prayer-'+overridePrayer.toLowerCase());
}
function openDayOverride(day) {
 overrideDay=day;
 const board=document.getElementById('day-override'),value=dayOverrides[overridePrayer]?.[day];
 board.querySelector('.content h2').textContent=overridePrayer+' · '+day;
 board.querySelector('.foot .primary').textContent='Save '+day;
 board.querySelector('[aria-label="Use prayer defaults"]').setAttribute('aria-checked',!value);
 board.querySelector('fieldset').disabled=!value;
 board.querySelectorAll('.seg button').forEach(b=>b.setAttribute('aria-pressed',b.textContent.trim()===(value?.sound||'Adhan')));
 board.querySelector('[aria-label="Vibrate"]').setAttribute('aria-checked',value?.vibrate||false);
}
function saveDayOverride() {
 const board=document.getElementById('day-override');
 dayOverrides[overridePrayer] ||= {};
 if(board.querySelector('[aria-label="Use prayer defaults"]').getAttribute('aria-checked')==='true')delete dayOverrides[overridePrayer][overrideDay];
 else dayOverrides[overridePrayer][overrideDay]={sound:board.querySelector('.seg button[aria-pressed="true"]').textContent.trim(),vibrate:board.querySelector('[aria-label="Vibrate"]').getAttribute('aria-checked')==='true'};
 refreshOverrideRows();
}
