// Cesium(3D 지도 라이브러리)은 JS 말고도 작업 파일(Workers)·이미지(Assets)·스타일(Widgets)을
// 주소로 직접 불러온다. 그래서 node_modules 에 있는 파일을 public/cesium 으로 복사해 둔다.
// npm run dev / build 전에 자동으로 돈다 (package.json 의 predev·prebuild). 복사본은 git 에 올리지 않는다.
import { cpSync, existsSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const src = join(root, 'node_modules', 'cesium', 'Build', 'Cesium')
const dest = join(root, 'public', 'cesium')

if (!existsSync(src)) {
  console.error('cesium 패키지가 없습니다. 먼저 npm install 을 실행하세요.')
  process.exit(1)
}
mkdirSync(dest, { recursive: true })
for (const dir of ['Workers', 'ThirdParty', 'Assets', 'Widgets']) {
  cpSync(join(src, dir), join(dest, dir), { recursive: true })
}
console.log('Cesium 파일 복사 완료 → public/cesium')
