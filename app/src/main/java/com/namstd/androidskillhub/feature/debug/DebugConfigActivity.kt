package com.namstd.androidskillhub.feature.debug

import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import com.namstd.androidskillhub.BuildConfig
import com.namstd.androidskillhub.core.config.DebugOverrides
import com.namstd.androidskillhub.core.config.RemoteConfig
import com.namstd.androidskillhub.core.ui.base.BaseActivity
import com.namstd.androidskillhub.databinding.ActivityDebugConfigBinding
import com.namstd.androidskillhub.databinding.ItemDebugConfigBinding

/**
 * [DEBUG] Màn chỉnh cấu hình ngay trên máy: cả khối `ad_config`, từng giá trị trong `settings`, hoặc từng vị trí
 * quảng cáo. Giá trị đặt ở đây (lưu qua [DebugOverrides]) thắng Firebase, áp dụng ở lần đọc kế tiếp. Chỉ lưu dòng nào
 * đã sửa, để các dòng còn lại vẫn theo `ad_config`. Chỉ vào được từ Settings ở bản DEBUG.
 */
class DebugConfigActivity : BaseActivity<ActivityDebugConfigBinding>() {
    private val fields = linkedMapOf<String, EditText>()

    /** Giá trị mỗi dòng lúc vừa hiện - dòng nào không đổi thì không lưu thành override. */
    private val shown = mutableMapOf<String, String>()

    override fun inflateBinding(inflater: LayoutInflater): ActivityDebugConfigBinding =
        ActivityDebugConfigBinding.inflate(inflater)

    override fun initViews() {
        // Số build là thứ điều kiện "App build" trên Firebase so sánh - xem docs/BASE_GUIDE.md (Remote Config theo phiên bản).
        binding.buildInfo.text = "App build ${BuildConfig.VERSION_CODE} (version ${BuildConfig.VERSION_NAME})"
        fillRows()
    }

    override fun initListeners() {
        binding.saveButton.setOnClickListener {
            fields.forEach { (key, editText) ->
                val value = editText.text.toString().trim()
                if (value == shown[key]) return@forEach
                if (value.isEmpty()) DebugOverrides.remove(key) else DebugOverrides.set(key, value)
            }
            fillRows()
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
        shown.clear()
        RemoteConfig.debugEntries().forEach { entry ->
            val row = ItemDebugConfigBinding.inflate(layoutInflater, binding.debugRows, false)
            row.rowKey.text = entry.key
            row.rowHint.text = entry.hint
            row.rowValue.setText(entry.value)
            binding.debugRows.addView(row.root)
            fields[entry.key] = row.rowValue
            shown[entry.key] = entry.value.trim()
        }
    }
}
