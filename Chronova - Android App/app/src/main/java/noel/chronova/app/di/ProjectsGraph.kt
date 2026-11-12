package noel.chronova.app.di

import java.time.ZoneId
import noel.chronova.app.AppContainer
import noel.chronova.feature.projects.domain.DeleteProject
import noel.chronova.feature.projects.domain.ObserveProjects
import noel.chronova.feature.projects.domain.ProjectUseCases
import noel.chronova.feature.projects.domain.SaveProject

/** The project list's wiring. */
class ProjectsGraph(private val app: AppContainer) {
    /** The time zone is read when the list opens. */
    fun useCases(): ProjectUseCases = ProjectUseCases(
        observe = ObserveProjects(app.projectRepository),
        save = SaveProject(app.projectRepository, ZoneId.systemDefault()),
        delete = DeleteProject(app.projectRepository),
    )
}
