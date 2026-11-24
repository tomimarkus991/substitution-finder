// Contours on a light base: the same drifting topographic field as contours.frag, but the lines are
// mixed in as faint ink instead of added as light, so they show on a pale background.
// Runs slower than the hero version. Used behind the logged-in pages.
#extension GL_OES_standard_derivatives : enable
precision mediump float;
uniform vec2 u_resolution;
uniform float u_time;
uniform vec3 u_bg, u_primary, u_secondary, u_primarySoft, u_secondarySoft, u_highlight;
float hash(vec2 p){ p=fract(p*vec2(123.34,456.21)); p+=dot(p,p+45.32); return fract(p.x*p.y); }
float noise(vec2 p){ vec2 i=floor(p), f=fract(p); vec2 u=f*f*(3.0-2.0*f);
  return mix(mix(hash(i),hash(i+vec2(1,0)),u.x), mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),u.x), u.y); }
float fbm(vec2 p){ float v=0.0,a=0.5; mat2 m=mat2(1.6,1.2,-1.2,1.6); for(int i=0;i<5;i++){ v+=a*noise(p); p=m*p; a*=0.5; } return v; }

void main(){ vec2 p=(gl_FragCoord.xy-0.5*u_resolution)/u_resolution.y; float t=u_time*0.02;
 float h=fbm(p*1.1+vec2(t,-t*0.6)+0.4*fbm(p*2.0-t));
 float lines=h*18.0;
 float line=1.0-clamp(abs(fract(lines)-0.5)/fwidth(lines)*0.9,0.0,1.0);
 float major=1.0-clamp(abs(fract(lines/5.0)-0.5)/fwidth(lines/5.0)*0.6,0.0,1.0);
 vec3 tint=mix(u_primary,u_secondary,smoothstep(0.35,0.75,h));
 vec3 col=mix(u_bg,u_primarySoft,0.22*smoothstep(0.3,0.8,h));
 col=mix(col,tint,line*0.12+major*0.2);
 col+=(hash(gl_FragCoord.xy+fract(u_time))-0.5)/160.0;
 gl_FragColor=vec4(clamp(col,0.0,1.0),1.0); }
