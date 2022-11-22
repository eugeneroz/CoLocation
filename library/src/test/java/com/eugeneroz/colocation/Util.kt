package com.eugeneroz.colocation

import android.os.Build
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

fun mockSdkInt(sdkInt: Int) {
    val sdkIntField = Build.VERSION::class.java.getField("SDK_INT")
    sdkIntField.isAccessible = true
    getModifiersField().also {
        it.isAccessible = true
        it.set(sdkIntField, sdkIntField.modifiers and Modifier.FINAL.inv())
    }
    sdkIntField.set(null, sdkInt)
}

private fun getModifiersField(): Field {
    return try {
        Field::class.java.getDeclaredField("modifiers")
    } catch (e: NoSuchFieldException) {
        try {
            val getDeclaredFields0: Method =
                Class::class.java.getDeclaredMethod(
                    "getDeclaredFields0",
                    Boolean::class.javaPrimitiveType
                )
            getDeclaredFields0.isAccessible = true
            val fields = getDeclaredFields0.invoke(Field::class.java, false) as Array<Field>
            for (field in fields) {
                if ("modifiers" == field.name) {
                    return field
                }
            }
        } catch (ex: ReflectiveOperationException) {
            e.addSuppressed(ex)
        }
        throw e
    }
}