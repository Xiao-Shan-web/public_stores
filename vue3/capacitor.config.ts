import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.sanda.app',
  appName: 'Sanda',
  webDir: 'dist',
  server: {
    // 使用 https scheme，便于调用宿主后端的 http 接口（需 cleartext）
    androidScheme: 'https',
    cleartext: true
  }
}

export default config
