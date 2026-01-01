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

    @javax.inject.Inject
    lateinit var modelRepository: com.example.placemate.data.repository.ModelRepository

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

        // Model Refresh Logic
        binding.btnRefreshModels.setOnClickListener {
            val apiKey = binding.editApiKey.text.toString()
            if (apiKey.isBlank()) {
                Toast.makeText(requireContext(), "Enter API Key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            binding.btnRefreshModels.isEnabled = false
            binding.btnRefreshModels.text = "Loading..."
            
            viewLifecycleOwner.lifecycleScope.launch {
                val models = modelRepository.fetchAvailableModels(apiKey)
                binding.btnRefreshModels.isEnabled = true
                binding.btnRefreshModels.text = "Refresh List"
                
                if (models.isNotEmpty()) {
                    val adapter = android.widget.ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        models.map { "${it.name} (${it.displayName})" }
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.spinnerGeminiModel.adapter = adapter
                    
                    // Try to re-select current model
                    val current = settingsRepository.getSelectedGeminiModel()
                    val index = models.indexOfFirst { it.name == current }
                    if (index >= 0) binding.spinnerGeminiModel.setSelection(index)
                    
                    Toast.makeText(requireContext(), "Found ${models.size} models", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "No models found or key invalid", Toast.LENGTH_LONG).show()
                }
            }
        }

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

        // Initial Model Logic (Fallback to XML if no dynamic list yet, or implemented slightly differently)
        // For now, we'll keep the XML list as default until user refreshes
        val models = resources.getStringArray(com.example.placemate.R.array.gemini_models)
        val currentModel = settingsRepository.getSelectedGeminiModel() 
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