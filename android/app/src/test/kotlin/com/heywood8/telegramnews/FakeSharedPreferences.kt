package com.heywood8.telegramnews

import android.content.SharedPreferences

/** In-memory SharedPreferences for unit tests — no Android runtime required. */
class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = data.toMap()

    override fun getString(key: String, defValue: String?): String? =
        if (data.containsKey(key)) data[key] as? String else defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        if (data.containsKey(key)) (data[key] as? Set<*>)?.map { it.toString() }?.toMutableSet()
        else defValues

    override fun getInt(key: String, defValue: Int): Int =
        if (data.containsKey(key)) data[key] as? Int ?: defValue else defValue

    override fun getLong(key: String, defValue: Long): Long =
        if (data.containsKey(key)) data[key] as? Long ?: defValue else defValue

    override fun getFloat(key: String, defValue: Float): Float =
        if (data.containsKey(key)) data[key] as? Float ?: defValue else defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        if (data.containsKey(key)) data[key] as? Boolean ?: defValue else defValue

    override fun contains(key: String): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removes = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending[key] = value; return this
        }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
            pending[key] = values; return this
        }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            pending[key] = value; return this
        }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            pending[key] = value; return this
        }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            pending[key] = value; return this
        }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            pending[key] = value; return this
        }
        override fun remove(key: String): SharedPreferences.Editor {
            removes.add(key); return this
        }
        override fun clear(): SharedPreferences.Editor {
            clearAll = true; return this
        }
        override fun commit(): Boolean { flush(); return true }
        override fun apply() { flush() }

        private fun flush() {
            if (clearAll) data.clear()
            removes.forEach { data.remove(it) }
            data.putAll(pending)
        }
    }
}
