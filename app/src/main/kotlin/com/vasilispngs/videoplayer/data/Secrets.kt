package com.vasilispngs.videoplayer.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class Secrets {
    private val key: SecretKey by lazy {
        val keyStore = KeyStore.getInstance(KEY_STORE).apply { load(null) }
        keyStore.getKey(ALIAS, null) as SecretKey? ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build()
            )
            generateKey()
        }
    }

    fun encrypt(text: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        return cipher.iv + cipher.doFinal(text.encodeToByteArray())
    }

    fun decrypt(data: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES))
        }
        return cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES).decodeToString()
    }

    private companion object {
        const val KEY_STORE = "AndroidKeyStore"
        const val ALIAS = "computers"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val IV_BYTES = 12
    }
}
