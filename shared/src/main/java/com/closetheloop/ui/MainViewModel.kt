package com.closetheloop.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.closetheloop.LoopApp
import com.closetheloop.data.Loop
import com.closetheloop.data.LoopRepository
import com.closetheloop.parser.ParsedTask
import com.closetheloop.parser.TaskParser
import java.time.Instant
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

    private val startOfToday: Long =
        Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate().atStartOfDay(zone()).toInstant().toEpochMilli()
    val closedToday: StateFlow<Int> = repository.observeClosedSince(startOfToday)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Add with an already-resolved deadline (e.g. a quick-time chip on the phone). */
    fun add(title: String, deadline: Long?) {
        viewModelScope.launch {
            repository.add(title, deadline)
            _saved.value = Confirmation(ParsedTask(title, deadline), edited = false)
        }
    }

    fun reopen(id: Long) {
        viewModelScope.launch { repository.reopen(id) }
    }

    fun save(spoken: String) {
        val task = TaskParser.parse(spoken, clock(), zone())
        viewModelScope.launch {
            repository.add(task.title, task.deadline)
            _saved.value = Confirmation(task, edited = false)
        }
    }

    /** Re-parses [text]; [deadline] (e.g. from a date picker) wins, otherwise if the text has no time only the title changes. */
    fun edit(id: Long, text: String, deadline: Long? = null) {
        val parsed = TaskParser.parse(text, clock(), zone())
        viewModelScope.launch {
            val loop = repository.edit(id, parsed.title, deadline ?: parsed.deadline) ?: return@launch
            _saved.value = Confirmation(ParsedTask(loop.title, loop.deadline), edited = true)
        }
    }

    fun dismissSaved() {
        _saved.value = null
    }

    fun close(id: Long) {
        viewModelScope.launch { repository.close(id) }
    }

    fun snooze(id: Long, minutes: Int) {
        viewModelScope.launch { repository.snooze(id, minutes) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LoopApp
                MainViewModel(app.repository, System::currentTimeMillis, ZoneId::systemDefault)
            }
        }
    }
}
