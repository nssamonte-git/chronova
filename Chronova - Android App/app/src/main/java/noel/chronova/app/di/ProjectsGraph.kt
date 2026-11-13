package noel.chronova.app.di

import java.time.ZoneId
import noel.chronova.app.AppContainer
import noel.chronova.core.domain.repository.ProjectQueries
import noel.chronova.feature.projects.domain.CopyProject
import noel.chronova.feature.projects.domain.DeleteProject
import noel.chronova.feature.projects.domain.ProjectUseCases
import noel.chronova.feature.projects.domain.SaveProject

/** The project list's wiring. */
class ProjectsGraph(private val app: AppContainer) {
    /** What screens read about projects (writes go through the use cases). */
    val queries: ProjectQueries get() = app.projectRepository

    /** The time zone is read when the list opens. */
    fun useCases(): ProjectUseCases = ProjectUseCases(
        save = SaveProject(app.projectRepository, ZoneId.systemDefault()),
        delete = DeleteProject(app.projectRepository),
        copy = CopyProject(app.projectRepository, ZoneId.systemDefault()),
    )
}
