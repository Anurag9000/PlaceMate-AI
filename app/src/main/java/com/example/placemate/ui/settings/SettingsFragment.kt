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

        binding.btnSaveSettings.setOnClickListener {
            val apiKey = binding.editApiKey.text?.toString() ?: ""
            viewLifecycleOwner.lifecycleScope.launch {
                settingsRepository.updateReminderCadence(binding.cadenceSlider.value.toInt())
                settingsRepository.updateGeminiApiKey(apiKey)
                Toast.makeText(requireContext(), "Settings saved!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}