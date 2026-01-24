package com.example.placemate.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.placemate.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()

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

        binding.btnRefreshModels.setOnClickListener {
            viewModel.fetchModels()
        }

        binding.textGetKeyLink.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/"))
            startActivity(intent)
        }

        binding.cadenceSlider.addOnChangeListener { _, value, _ ->
            viewModel.onCadenceChanged(value.toInt())
        }

        binding.switchUseGemini.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onUseGeminiChanged(isChecked)
        }

        binding.btnResetPrompt.setOnClickListener {
            viewModel.resetPrompt()
        }

        binding.btnSaveSettings.setOnClickListener {
            viewModel.onApiKeyChanged(binding.editApiKey.text.toString())
            viewModel.onPromptChanged(binding.editCustomPrompt.text.toString())
            val selectedModel = binding.spinnerGeminiModel.selectedItem?.toString() ?: ""
            viewModel.onModelSelected(selectedModel)
            viewModel.saveSettings()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (binding.editApiKey.text.toString() != state.apiKey && !binding.editApiKey.hasFocus()) {
                        binding.editApiKey.setText(state.apiKey)
                    }
                    if (binding.editCustomPrompt.text.toString() != state.customPrompt && !binding.editCustomPrompt.hasFocus()) {
                        binding.editCustomPrompt.setText(state.customPrompt)
                    }
                    
                    binding.switchUseGemini.isChecked = state.useGemini
                    binding.layoutApiKey.isEnabled = state.useGemini
                    binding.spinnerGeminiModel.isEnabled = state.useGemini
                    binding.layoutCustomPrompt.isEnabled = state.useGemini
                    binding.btnResetPrompt.isEnabled = state.useGemini
                    
                    binding.cadenceSlider.value = state.reminderCadence.toFloat()
                    binding.textCadenceValue.text = "${state.reminderCadence} hours"

                    if (state.availableModels.isNotEmpty()) {
                        val adapter = android.widget.ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_spinner_item,
                            state.availableModels
                        )
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        binding.spinnerGeminiModel.adapter = adapter
                        
                        val index = state.availableModels.indexOfFirst { it.startsWith(state.selectedModel) }
                        if (index >= 0) binding.spinnerGeminiModel.setSelection(index)
                    }

                    binding.btnRefreshModels.isEnabled = !state.isLoadingModels
                    binding.btnRefreshModels.text = if (state.isLoadingModels) "Loading..." else "Refresh List"

                    if (state.saveSuccess) {
                        Toast.makeText(requireContext(), "Settings saved!", Toast.LENGTH_SHORT).show()
                        viewModel.consumeSaveSuccess()
                    }
                }
            }
        }
    }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}