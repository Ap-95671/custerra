// Writes a secret file without printing keys. Never overwrite an existing key pair.
import { generateKeyPairSync } from 'node:crypto';
import { writeFileSync } from 'node:fs';
const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const pub = publicKey.export({ format: 'jwk' }); const secret = privateKey.export({ format: 'jwk' });
const encoded = Buffer.concat([Buffer.from([4]), Buffer.from(pub.x, 'base64url'), Buffer.from(pub.y, 'base64url')]).toString('base64url');
writeFileSync('.env.push', `VAPID_PUBLIC_KEY=${encoded}\nVAPID_PRIVATE_KEY=${secret.d}\nVAPID_SUBJECT=\n`, { mode: 0o600, flag: 'wx' });
console.log('Created .env.push with private permissions. Set VAPID_SUBJECT to your contact URI, then export this file when starting the backend.');
