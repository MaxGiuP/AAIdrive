package io.bimmergestalt.idriveconnectaddons.screenmirror

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import androidx.activity.viewModels
import io.bimmergestalt.idriveconnectaddons.screenmirror.databinding.ActivityMainBinding

const val TAG = "ScreenMirroring"

class MainActivity : AppCompatActivity() {
    val controller by lazy { MainController(this) }
    val viewModel by viewModels<MainModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityMainBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this
        binding.controller = controller
        binding.viewModel = viewModel
        setContentView(binding.root)
        val settings = ProjectionSettings(this)
        binding.projectionProfile.setSelection(settings.profile.ordinal)
        binding.knobMode.setSelection(settings.knobMode.ordinal)
        binding.reverseKnob.isChecked = settings.reverseKnob
        binding.projectionProfile.onSelected { settings.profile = ProjectionProfile.values()[it] }
        binding.knobMode.onSelected { settings.knobMode = KnobMode.values()[it] }
        binding.reverseKnob.setOnCheckedChangeListener { _, checked -> settings.reverseKnob = checked }
    }

    private fun Spinner.onSelected(action: (Int) -> Unit) {
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                action(position)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updatePermissions(this)
    }
}