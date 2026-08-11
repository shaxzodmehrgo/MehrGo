package uz.teamwork.mehrgodriver.common

/** Общий флаг «водитель на линии» для MaktabGo (back-обработка в разных экранах). */
object MaktabGoSession {
    @Volatile var online: Boolean = false
}
