/**
 * Builds the app's assistant orb from the supplied model, as is apart from what
 * Filament needs and the look tuned against docs/brand/assistant-orb-reference.webp:
 *
 *   docs/brand/assistant-orb.glb  →  Chronova - Android App/app/src/main/assets/orb/assistant_orb.glb
 *
 * - Draco geometry is decoded (Filament's Android loader doesn't promise Draco).
 * - WebP textures become JPEG/PNG at app size (Filament reads PNG, JPEG, KTX2 only):
 *   colour 1024 px JPEG, normal 512 px PNG.
 * - Material: a lighter blue tint, glassy (roughness 0.08, metallic 0.15 instead of the
 *   baked metal/roughness map) and the colour map reused as a glow map. The cyan/magenta
 *   lighting and the centre spark are added in the app.
 *
 * Run: cd tools/brand/orb && npm ci && npm run convert   (re-running gives the same file)
 */
import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { NodeIO } from '@gltf-transform/core';
import { ALL_EXTENSIONS } from '@gltf-transform/extensions';
import draco3d from 'draco3dgltf';
import sharp from 'sharp';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const source = path.join(root, 'docs', 'brand', 'assistant-orb.glb');
const target = path.join(root, 'Chronova - Android App', 'app', 'src', 'main', 'assets', 'orb', 'assistant_orb.glb');

// sRGB #d8e4ff in linear terms: a light blue tint that lifts the baked violet toward the reference.
const srgbToLinear = (c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
const TINT = [0xd8, 0xe4, 0xff].map((v) => srgbToLinear(v / 255));

const io = new NodeIO()
  .registerExtensions(ALL_EXTENSIONS)
  .registerDependencies({ 'draco3d.decoder': await draco3d.createDecoderModule() });

const doc = await io.read(source);
const root_ = doc.getRoot();

// Geometry: decoded on read; dropping the extension writes it uncompressed.
for (const ext of root_.listExtensionsUsed()) {
  if (ext.extensionName === 'KHR_draco_mesh_compression') ext.dispose();
}

const material = root_.listMaterials()[0];
const baseColor = material.getBaseColorTexture();
const normal = material.getNormalTexture();
const metalRough = material.getMetallicRoughnessTexture();

async function reencode(texture, size, format, name) {
  const image = sharp(Buffer.from(texture.getImage())).resize(size, size, { fit: 'fill' });
  const data = format === 'jpeg'
    ? await image.removeAlpha().jpeg({ quality: 88, mozjpeg: true }).toBuffer()
    : await image.png({ compressionLevel: 9, palette: false }).toBuffer();
  texture.setImage(new Uint8Array(data)).setMimeType(format === 'jpeg' ? 'image/jpeg' : 'image/png').setURI(name);
}

await reencode(baseColor, 1024, 'jpeg', 'base_color.jpg');
await reencode(normal, 512, 'png', 'normal.png');

material.setMetallicRoughnessTexture(null);
metalRough?.dispose();
material
  .setBaseColorFactor([...TINT, 1])
  .setMetallicFactor(0.15)
  .setRoughnessFactor(0.08)
  .setEmissiveTexture(baseColor)
  .setEmissiveFactor([0.8, 0.8, 0.8]);

await mkdir(path.dirname(target), { recursive: true });
await io.write(target, doc);
const out = await io.readBinary(await io.writeBinary(doc));
console.log('wrote', path.relative(root, target));
console.log('extensions:', out.getRoot().listExtensionsUsed().map((e) => e.extensionName).join(', ') || 'none');
console.log('textures:', out.getRoot().listTextures().map((t) => `${t.getURI()} ${t.getMimeType()} ${t.getImage().byteLength} B`).join('; '));
