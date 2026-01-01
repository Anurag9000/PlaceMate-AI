package com.example.placemate.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.placemate.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    @javax.inject.Inject
    lateinit var settingsRepository: com.example.placemate.data.repository.SettingsRepository

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load existing settings
        val currentApiKey = settingsRepository.getGeminiApiKey()
        binding.editApiKey.setText(currentApiKey)

        viewLifecycleOwner.lifecycleScope.launch {
            settingsRepository.reminderCadenceHours.collect { hours ->
                binding.cadenceSlider.value = hours.toFloat()
                binding.textCadenceValue.text = "$hours hours"
            }
        }

        binding.textGetKeyLink.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/"))
            startActivity(intent)
        }

        binding.cadenceSlider.addOnChangeListener { _, value, _ ->
            binding.textCadenceValue.text = "${value.toInt()} hours"
        }

        // Toggle state
        binding.switchUseGemini.isChecked = settingsRepository.isGeminiEnabled()
        binding.layoutApiKey.isEnabled = binding.switchUseGemini.isChecked
        binding.spinnerGeminiModel.isEnabled = binding.switchUseGemini.isChecked

        // Model Spinner Setup
        val models = resources.getStringArray(com.example.placemate.R.array.gemini_models)
        val currentModel = settingsRepository.getSelectedGeminiModel() // Assume this method exists or is added via extension/wrapper
        val modelIndex = models.indexOfFirst { it.startsWith(currentModel) }
        if (modelIndex >= 0) {
            binding.spinnerGeminiModel.setSelection(modelIndex)
        }

        binding.switchUseGemini.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutApiKey.isEnabled = isChecked
            binding.spinnerGeminiModel.isEnabled = isChecked
        }

        binding.btnSaveSettings.setOnClickListener {
            val apiKey = binding.editApiKey.text?.toString() ?: ""
            val useGemini = binding.switchUseGemini.isChecked
            val selectedModelString = binding.spinnerGeminiModel.selectedItem.toString()
            val selectedModel = selectedModelString.split(" ")[0] // Extract "gemini-1.5-flash" from "gemini-1.5-flash (Fastest...)"
            
            if (useGemini && apiKey.isBlank()) {
                Toast.makeText(requireContext(), "Please enter a Gemini API Key to enable it.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                settingsRepository.updateReminderCadence(binding.cadenceSlider.value.toInt())
                settingsRepository.updateGeminiApiKey(apiKey)
                settingsRepository.setUseGemini(useGemini)
                settingsRepository.setSelectedGeminiModel(selectedModel)
                Toast.makeText(requireContext(), "Settings saved! Using $selectedModel", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}