import { NextRequest } from 'next/server';
export const runtime='nodejs';
export const dynamic='force-dynamic';
async function proxy(request:NextRequest) {
  const url=new URL(request.url);
  if(!url.pathname.startsWith('/api/v1/'))return Response.json({status:404,error:'Not Found'},{status:404});
  const internal=process.env.BACKEND_INTERNAL_URL ?? (process.env.BACKEND_HOST ? `http://${process.env.BACKEND_HOST}:8080` : undefined);
  if(!internal)return Response.json({status:503,error:'Service Unavailable',message:'API connection is not configured.'},{status:503});
  let body:Uint8Array|undefined;
  if(request.body) {
    const reader=request.body.getReader();const chunks:Uint8Array[]=[];let size=0;
    while(true){const part=await reader.read();if(part.done)break;size+=part.value.length;if(size>1_048_576){await reader.cancel();return Response.json({status:413,error:'Payload Too Large'},{status:413});}chunks.push(part.value);}
    body=new Uint8Array(size);let offset=0;for(const chunk of chunks){body.set(chunk,offset);offset+=chunk.length;}
  }
  const headers=new Headers();
  for(const name of ['content-type','accept','cookie','authorization','x-csrf-token']){const value=request.headers.get(name);if(value)headers.set(name,value);}
  try {
    const target=new URL(url.pathname+url.search,internal);
    const response=await fetch(target,{method:request.method,headers,body:body as BodyInit|undefined,redirect:'manual',cache:'no-store',signal:AbortSignal.timeout(40000)});
    const outgoing=new Headers({'Cache-Control':'no-store'});
    for(const name of ['content-type','retry-after']){const value=response.headers.get(name);if(value)outgoing.set(name,value);}
    for(const cookie of response.headers.getSetCookie())outgoing.append('Set-Cookie',cookie);
    return new Response(response.body,{status:response.status,headers:outgoing});
  } catch {return Response.json({status:503,error:'Service Unavailable',message:'The API is temporarily unavailable.'},{status:503});}
}
export {proxy as GET,proxy as POST,proxy as PATCH,proxy as DELETE,proxy as PUT,proxy as OPTIONS,proxy as HEAD};
