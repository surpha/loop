package com.closetheloop.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.closetheloop.CloseTheLoopApp
import com.closetheloop.data.Loop
import com.closetheloop.data.LoopRepository
import com.closetheloop.parser.ParsedTask
import com.closetheloop.parser.TaskParser
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: LoopRepository,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) : ViewModel() {
    val openLoops: StateFlow<List<Loop>> = repository.observeOpen()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    data class Confirmation(val task: ParsedTask, val edited: Boolean)

    private val _saved = MutableStateFlow<Confirmation?>(null)
    val saved: StateFlow<Confirmation?> = _saved.asStateFlow()

    fun save(spoken: String) {
        val task = TaskParser.parse(spoken, clock(), zone())
        viewModelScope.launch {
            repository.add(task.title, task.deadline)
            _saved.value = Confirmation(task, edited = false)
        }
    }

    /** Re-parses [text]; if it contains no time, only the title changes. */
    fun edit(id: Long, text: String) {
        val parsed = TaskParser.parse(text, clock(), zone())
        viewModelScope.launch {
            val loop = repository.edit(id, parsed.title, parsed.deadline) ?: return@launch
            _saved.value = Confirmation(ParsedTask(loop.title, loop.deadline), edited = true)
        }
    }

    fun dismissSaved() {
        _saved.value = null
    }

    fun close(id: Long) {
        viewModelScope.launch { repository.close(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CloseTheLoopApp
                MainViewModel(app.container.repository, System::currentTimeMillis, ZoneId::systemDefault)
            }
        }
    }
}
