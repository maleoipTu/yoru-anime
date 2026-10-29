import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
const html=fs.readFileSync('Yoru/Assets/PlayerHost/index.html','utf8');
assert.equal(html.trim(),fs.readFileSync('android/app/src/main/assets/player/index.html','utf8').trim(),'Windows and Android use the same corrected embed');
const script=html.match(/<script>([\s\S]*?)<\/script>/)[1];
function host(platform='windows',url='https://video.example/episode/1') {
 const handlers={},frameHandlers={},messages=[];
 const frame={contentWindow:{},addEventListener:(k,fn)=>frameHandlers[k]=fn,remove(){}};
 const context={URL,URLSearchParams,location:{search:'?src='+encodeURIComponent(url)+'&session=42',origin:'https://player.yoru.local'},document:{getElementById:()=>frame,body:{}},window:{addEventListener:(k,fn)=>handlers[k]=fn}};
 if(platform==='windows')context.window.chrome={webview:{postMessage:m=>messages.push(m)}};
 vm.runInNewContext(script,context);
 return {context,frame,handlers,frameHandlers,messages};
}
const pc=host();assert.equal(pc.frame.src,'https://video.example/episode/1');
pc.frameHandlers.load();assert.equal(pc.messages[0].state,'frame-ready');assert.equal(pc.messages[0].session,'42');
pc.handlers.message({source:{},origin:'https://video.example',data:{key:'player_play'}});assert.equal(pc.messages.length,1);
pc.handlers.message({source:pc.frame.contentWindow,origin:'https://other.example',data:{key:'player_play'}});assert.equal(pc.messages.length,1);
pc.handlers.message({source:pc.frame.contentWindow,origin:'https://video.example',data:{key:'kodik_player_time_update',value:12}});assert.equal(pc.messages.at(-1).value,12);
const invalid=host('windows','javascript:alert(1)');assert.equal(invalid.messages[0].state,'invalid-source');
const android=host('android');android.frameHandlers.load();const received=[];const port={postMessage:m=>received.push(JSON.parse(m))};
android.handlers.message({source:android.frame.contentWindow,origin:'https://video.example',data:'yoru-connect',ports:[port]});assert.equal(received.length,0);
android.handlers.message({source:null,origin:'',data:'yoru-connect',ports:[port]});assert.equal(received[0].state,'frame-ready');assert.equal(received[0].session,'42');
android.handlers.message({source:android.frame.contentWindow,origin:'https://video.example',data:{key:'player_video_started'}});assert.equal(received.at(-1).state,'playing');
console.log('PASS: shared iframe, session identity, provider origin checks, Android message port, pending states, media state forwarding.');
