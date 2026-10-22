package com.henry.chord

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ImportUiState(val session: ImportSession? = null, val busy: Boolean = false, val progress: ImportProgress? = null, val error: String? = null)
class ImportController(context: Context, private val dao: ChordDao) : ViewModel() {
    private val manager = BulkImportManager(context.applicationContext, dao)
    private val mutable = MutableStateFlow(ImportUiState(busy = true))
    val state: StateFlow<ImportUiState> = mutable
    private var job: Job? = null
    private val mutex = Mutex()
    init { viewModelScope.launch(Dispatchers.IO) { mutable.value = ImportUiState(session = manager.recover()) } }
    fun choose(uris: List<Uri> = emptyList(), tree: Uri? = null) = run {
        job = viewModelScope.launch(Dispatchers.IO) {
            mutable.update { it.copy(busy = true, error = null) }
            try {
                val session = manager.discover(uris, tree) { p -> mutable.update { it.copy(progress = p) } }
                mutable.value = ImportUiState(session)
            } catch (e: CancellationException) { mutable.update { it.copy(busy = false, progress = null, error = "Discovery cancelled. Choose sources again to retry.") }; throw e }
            catch (e: Exception) { mutable.update { it.copy(busy = false, error = e.message, progress = null) } }
        }
    }
    fun choice(id: String, choice: ImportChoice) { viewModelScope.launch(Dispatchers.IO) { mutex.withLock {
        val session = mutable.value.session ?: return@withLock
        val updated = session.copy(candidates = session.candidates.map { if (it.id == id) it.copy(choice = choice) else it })
        manager.save(updated); mutable.update { it.copy(session = updated) }
    } } }
    fun review(id: String, text: String?) { viewModelScope.launch(Dispatchers.IO) { mutex.withLock {
        try { val session = mutable.value.session ?: return@withLock; val updated = manager.review(session, id, text); mutable.update { it.copy(session = updated) } }
        catch (e: Exception) { mutable.update { it.copy(error = "Review could not be saved: ${e.message}") } }
    } } }
    fun commit() {
        job = viewModelScope.launch(Dispatchers.IO) { mutex.withLock {
            val session = mutable.value.session ?: return@withLock
            mutable.update { it.copy(busy = true, error = null) }
            try { val result = manager.commit(session) { p -> mutable.update { it.copy(progress = p) } }; mutable.value = ImportUiState(result) }
            catch (e: CancellationException) { mutable.value = ImportUiState(manager.recover(), error = "Import paused. Committed songs are safe. Resume to continue."); throw e }
            catch (e: Exception) { mutable.value = ImportUiState(manager.recover(), error = e.message) }
        } }
    }
    fun cancel() { job?.cancel() }
    fun reset() { viewModelScope.launch(Dispatchers.IO) { mutex.withLock { mutable.value.session?.let { manager.discard(it) }; mutable.value = ImportUiState() } } }
}
