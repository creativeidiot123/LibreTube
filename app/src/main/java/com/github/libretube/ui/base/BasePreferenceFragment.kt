package com.github.libretube.ui.base

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.github.libretube.R
import com.github.libretube.databinding.DialogTextPreferenceBinding
import com.github.libretube.ui.activities.SettingsActivity
import com.github.libretube.ui.extensions.onSystemInsets
import android.content.SharedPreferences
import com.github.libretube.ui.preferences.EditNumberPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.preference.PreferenceGroup
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.ui.preferences.BackupRestoreSettings

/**
 * PreferenceFragmentCompat using the [MaterialAlertDialogBuilder] instead of the old dialog builder
 */
abstract class BasePreferenceFragment : PreferenceFragmentCompat() {
    abstract val titleResourceId: Int

    private val settingsActivity get() = activity as? SettingsActivity

    /**
     * Whether any preference dialog is currently visible to the user.
     */
    var isDialogVisible = false

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "lock_settings") {
            checkSettingsLock()
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        checkSettingsLock()
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun checkSettingsLock() {
        val lockSwitch = findPreference<androidx.preference.SwitchPreferenceCompat>("lock_settings")
        lockSwitch?.setOnPreferenceChangeListener { preference, newValue ->
            val isLocking = newValue as Boolean
            if (isLocking) {
                // Locking
                val currentPassword = PreferenceHelper.getLockPassword()
                if (currentPassword.isEmpty()) {
                    showSetPasswordDialog(preference as androidx.preference.SwitchPreferenceCompat)
                    false // Don't toggle yet
                } else {
                    true // Allow toggle
                }
            } else {
                // Unlocking
                showUnlockDialog(preference as androidx.preference.SwitchPreferenceCompat)
                false // Don't toggle yet
            }
        }

        val isLocked = PreferenceHelper.getBoolean("lock_settings", false)
        val isBackupSettings = this is BackupRestoreSettings

        // If we are in Backup settings, we don't need to lock anything.
        if (isBackupSettings) return

        updatePreferencesRecursively(preferenceScreen, isLocked)
    }

    private fun showSetPasswordDialog(preference: androidx.preference.SwitchPreferenceCompat) {
        val binding = DialogTextPreferenceBinding.inflate(layoutInflater)
        binding.input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        binding.input.hint = getString(R.string.lock_settings_password_hint)

        isDialogVisible = true
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lock_settings_set_password_title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val password = binding.input.text.toString()
                if (password.isNotEmpty()) {
                    PreferenceHelper.setLockPassword(password)
                    preference.isChecked = true
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { isDialogVisible = false }
            .show()
    }

    private fun showUnlockDialog(preference: androidx.preference.SwitchPreferenceCompat) {
        val binding = DialogTextPreferenceBinding.inflate(layoutInflater)
        binding.input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        binding.input.hint = getString(R.string.lock_settings_password_hint)

        isDialogVisible = true
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lock_settings_password_title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val input = binding.input.text.toString()
                if (input == PreferenceHelper.getLockPassword()) {
                    preference.isChecked = false
                } else {
                    Toast.makeText(requireContext(), R.string.lock_settings_password_error, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { isDialogVisible = false }
            .show()
    }

    private fun updatePreferencesRecursively(group: PreferenceGroup, isLocked: Boolean) {
        for (i in 0 until group.preferenceCount) {
            val preference = group.getPreference(i)

            if (preference is PreferenceGroup) {
                // Don't disable groups (categories), so their enabled children remain interactive
                updatePreferencesRecursively(preference, isLocked)
            } else {
                if (preference.key == "lock_settings" || preference.key == "backup_restore") {
                    preference.isEnabled = true
                } else {
                    preference.isEnabled = !isLocked
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        settingsActivity?.changeTopBarText(getString(titleResourceId))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // add bottom padding to the list, to ensure that the last item is not overlapped by the system bars
        listView.onSystemInsets { v, systemInsets ->
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                v.paddingBottom + systemInsets.bottom
            )
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        // can be set to true here since we only use the following preferences with dialogs
        isDialogVisible = true

        when (preference) {
            /**
             * Show a [MaterialAlertDialogBuilder] when the preference is a [ListPreference]
             */
            is ListPreference -> {
                // get the index of the previous selected item
                val prefIndex = preference.entryValues.indexOf(preference.value)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(preference.title)
                    .setSingleChoiceItems(preference.entries, prefIndex) { dialog, index ->
                        // get the new ListPreference value
                        val newValue = preference.entryValues[index].toString()
                        // invoke the on change listeners
                        if (preference.callChangeListener(newValue)) {
                            preference.value = newValue
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setOnDismissListener { isDialogVisible = false }
                    .show()
            }

            is MultiSelectListPreference -> {
                val selectedItems = preference.entryValues.map {
                    preference.values.contains(it)
                }.toBooleanArray()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(preference.title)
                    .setMultiChoiceItems(preference.entries, selectedItems) { _, _, _ ->
                        val newValues = preference.entryValues
                            .filterIndexed { index, _ -> selectedItems[index] }
                            .map { it.toString() }
                            .toMutableSet()
                        if (preference.callChangeListener(newValues)) {
                            preference.values = newValues
                        }
                    }
                    .setPositiveButton(R.string.okay, null)
                    .setOnDismissListener { isDialogVisible = false }
                    .show()
            }

            is EditTextPreference -> {
                val binding = DialogTextPreferenceBinding.inflate(layoutInflater)
                binding.input.setText(preference.text)

                if (preference is EditNumberPreference) {
                    binding.input.inputType = InputType.TYPE_NUMBER_FLAG_SIGNED
                }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(preference.title)
                    .setView(binding.root)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val newValue = binding.input.text.toString()
                        if (preference is EditNumberPreference && newValue.toIntOrNull() == null) {
                            Toast.makeText(context, R.string.invalid_input, Toast.LENGTH_LONG).show()
                            return@setPositiveButton
                        }

                        if (preference.callChangeListener(newValue)) {
                            preference.text = newValue
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setOnDismissListener { isDialogVisible = false }
                    .show()
            }
            /**
             * Otherwise show the normal dialog, dialogs for other preference types are not supported yet,
             * nor used anywhere inside the app
             */
            else -> super.onDisplayPreferenceDialog(preference)
        }
    }
}
