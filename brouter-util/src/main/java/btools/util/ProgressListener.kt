package btools.util


interface ProgressListener {
    fun updateProgress(task: String?, progress: Int)

    val isCanceled: Boolean
}
