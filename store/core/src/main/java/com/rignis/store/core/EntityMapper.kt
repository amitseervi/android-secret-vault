package com.rignis.store.core

import com.rignis.store.api.EncryptedDataEntry
import com.rignis.store.api.EncryptedDataItem
import com.rignis.store.api.EncryptedDataRef
import com.rignis.store.core.local.SecretData
import com.rignis.store.core.local.SecretDataRef

object EntityMapper {
    fun toEncryptedDataItem(entity: SecretData): EncryptedDataItem {
        return EncryptedDataItem(
            entity.id, entity.title, entity.encryptedData, entity.initializationVector
        )
    }

    fun toEncryptedDataRef(entityRef: SecretDataRef): EncryptedDataRef {
        return EncryptedDataRef(entityRef.id, entityRef.title)
    }

    fun toEntity(id: String, entry: EncryptedDataEntry): SecretData {
        return SecretData(
            id = id,
            title = entry.title,
            encryptedData = entry.encryptedBody,
            initializationVector = entry.initializationVector
        )
    }
}