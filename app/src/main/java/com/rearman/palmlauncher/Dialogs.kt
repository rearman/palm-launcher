package com.rearman.palmlauncher

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import android.widget.Toast

// Single-choice list of state.categories (no All); `current` is checked. Calls onPick(id).
fun pickCategory(act: Activity, state: LauncherState, current: Int, onPick: (Int) -> Unit) {
	val names = state.categories.map { it.name }.toTypedArray<CharSequence>()
	val checked = state.categories.indexOfFirst { it.id == current }
	AlertDialog.Builder(act)
		.setTitle("Category")
		.setSingleChoiceItems(names, checked) { dialog, which ->
			onPick(state.categories[which].id)
			dialog.dismiss()
		}
		.setNegativeButton("Cancel", null)
		.show()
}

// Palm "Edit Categories". Calls onChange() after each change (caller saves and redraws).
fun editCategories(act: Activity, state: LauncherState, onChange: () -> Unit) {
	val editable = state.categories.filter { it.id != UNFILED }
	AlertDialog.Builder(act)
		.setTitle("Edit Categories")
		.setItems(editable.map { it.name }.toTypedArray<CharSequence>()) { _, which ->
			categoryMenu(act, state, editable[which], onChange)
		}
		.setPositiveButton("New") { _, _ -> nameDialog(act, state, null, onChange) }
		.setNegativeButton("OK", null)
		.show()
}

// Actions for one category: Rename or Delete.
private fun categoryMenu(act: Activity, state: LauncherState, category: Category, onChange: () -> Unit) {
	AlertDialog.Builder(act)
		.setTitle(category.name)
		.setItems(arrayOf<CharSequence>("Rename", "Delete")) { _, which ->
			if (which == 0) {
				nameDialog(act, state, category, onChange)
			} else {
				confirmDelete(act, state, category, onChange)
			}
		}
		.show()
}

// Name entry for a new category (category == null) or a rename.
private fun nameDialog(act: Activity, state: LauncherState, category: Category?, onChange: () -> Unit) {
	val pad = (20 * act.resources.displayMetrics.density).toInt()
	val input = EditText(act).apply {
		setSingleLine(true)
		setPadding(pad, pad, pad, pad)
		setText(category?.name.orEmpty())
		setSelection(text.length)
	}
	AlertDialog.Builder(act)
		.setTitle(if (category == null) "New Category" else "Rename Category")
		.setView(input)
		.setPositiveButton("OK") { _, _ ->
			val name = input.text.toString().trim()
			val clash = state.categories.any {
				it.id != category?.id && it.name.equals(name, ignoreCase = true)
			}
			if (name.isEmpty() || clash) {
				Toast.makeText(act, "Enter a different name.", Toast.LENGTH_SHORT).show()
			} else {
				if (category == null) {
					state.addCategory(name)
				} else {
					state.renameCategory(category.id, name)
				}
				onChange()
				editCategories(act, state, onChange)
			}
		}
		.setNegativeButton("Cancel", null)
		.show()
}

private fun confirmDelete(act: Activity, state: LauncherState, category: Category, onChange: () -> Unit) {
	AlertDialog.Builder(act)
		.setMessage("Delete category \"${category.name}\"? Its applications move to Unfiled.")
		.setPositiveButton("Delete") { _, _ ->
			state.deleteCategory(category.id)
			onChange()
			editCategories(act, state, onChange)
		}
		.setNegativeButton("Cancel", null)
		.show()
}
