package com.namstd.androidskillhub.feature.debug

import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import com.namstd.androidskillhub.core.config.DebugOverrides
import com.namstd.androidskillhub.core.config.RemoteConfig
import com.namstd.androidskillhub.core.ui.base.BaseActivity
import com.namstd.androidskillhub.databinding.ActivityDebugConfigBinding
import com.namstd.androidskillhub.databinding.ItemDebugConfigBinding

/**
 * [DEBUG] Màn chỉnh raw value từng Remote Config key ngay trên máy. Giá trị đặt ở đây (lưu qua
 * [DebugOverrides]) thắng Firebase/default, áp dụng ở lần đọc kế tiếp. Chỉ vào được từ Settings ở bản DEBUG.
 */
class DebugConfigActivity : BaseActivity<ActivityDebugConfigBinding>() {
    private val fields = linkedMapOf<String, EditText>()

    override fun inflateBinding(inflater: LayoutInflater): ActivityDebugConfigBinding =
        ActivityDebugConfigBinding.inflate(inflater)

    override fun initViews() {
        fillRows()
    }

    override fun initListeners() {
        binding.saveButton.setOnClickListener {
            fields.forEach { (key, editText) ->
                val value = editText.text.toString().trim()
                if (value.isEmpty()) DebugOverrides.remove(key) else DebugOverrides.set(key, value)
            }
            Toast.makeText(this, "Đã lưu override. Mở lại màn tương ứng để thấy hiệu lực.", Toast.LENGTH_SHORT).show()
        }
        binding.resetButton.setOnClickListener {
            DebugOverrides.clear()
            fillRows()
            Toast.makeText(this, "Đã xoá hết override.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Dựng (lại) mỗi Remote Config key một dòng sửa được, điền sẵn giá trị đang có hiệu lực của nó. */
    private fun fillRows() {
        binding.debugRows.removeAllViews()
        fields.clear()
        RemoteConfig.debugEntries().forEach { entry ->
            val row = ItemDebugConfigBinding.inflate(layoutInflater, binding.debugRows, false)
            row.rowKey.text = entry.key
            row.rowHint.text = entry.hint
            row.rowValue.setText(entry.value)
            binding.debugRows.addView(row.root)
            fields[entry.key] = row.rowValue
        }
    }
}
