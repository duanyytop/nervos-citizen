package org.nervos.gw

import androidx.multidex.MultiDexApplication
import org.spongycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class MainApplication : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}