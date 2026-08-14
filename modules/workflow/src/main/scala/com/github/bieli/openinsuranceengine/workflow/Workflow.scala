package com.github.bieli.openinsuranceengine.workflow

import cats.effect.Sync
import cats.syntax.all.*
import com.github.bieli.openinsuranceengine.core.id.{EntityId, WorkflowId, WorkflowTag}
import com.github.bieli.openinsuranceengine.core.result.{DomainError, DomainResult}
import com.github.bieli.openinsuranceengine.core.time.EffectiveInstant

/**
 * Generic workflow engine - models domain typical style activities / process flows.
 * Steps are pure transitions over a typed payload `S` (state).
 */
enum StepStatus:
  case Pending, Active, Completed, Skipped, Failed, Waiting

object StepStatus:
  given CanEqual[StepStatus, StepStatus] = CanEqual.derived

enum WorkflowStatus:
  case Draft, Running, Completed, Failed, Cancelled, Suspended

object WorkflowStatus:
  given CanEqual[WorkflowStatus, WorkflowStatus] = CanEqual.derived

final case class Transition[S](
    to: String,
    guard: S => Boolean = (_: S) => true,
    description: String = ""
)

final case class WorkflowStep[S, F[_]](
    id: String,
    name: String,
    execute: S => F[DomainResult[S]],
    transitions: List[Transition[S]],
    isTerminal: Boolean = false
)

final case class WorkflowDefinition[S, F[_]](
    id: String,
    name: String,
    initialStepId: String,
    steps: Map[String, WorkflowStep[S, F]]
):
  def step(id: String): Option[WorkflowStep[S, F]] = steps.get(id)

final case class StepExecution(
    stepId: String,
    status: StepStatus,
    startedAt: Option[EffectiveInstant] = None,
    completedAt: Option[EffectiveInstant] = None,
    error: Option[String] = None
)

final case class WorkflowInstance[S](
    id: WorkflowId,
    definitionId: String,
    status: WorkflowStatus,
    currentStepId: Option[String],
    state: S,
    history: List[StepExecution],
    createdAt: EffectiveInstant,
    updatedAt: EffectiveInstant
)

trait WorkflowEngine[F[_], S]:
  def start(definition: WorkflowDefinition[S, F], initial: S): F[DomainResult[WorkflowInstance[S]]]
  def advance(instance: WorkflowInstance[S], definition: WorkflowDefinition[S, F]): F[DomainResult[WorkflowInstance[S]]]
  def cancel(instance: WorkflowInstance[S], reason: String): F[WorkflowInstance[S]]

object WorkflowEngine:
  def apply[F[_]: Sync, S]: WorkflowEngine[F, S] = new WorkflowEngine[F, S]:

    def start(
        definition: WorkflowDefinition[S, F],
        initial: S
    ): F[DomainResult[WorkflowInstance[S]]] =
      Sync[F].realTimeInstant.map: now =>
        definition.step(definition.initialStepId) match
          case None =>
            DomainResult.raise(
              DomainError.WorkflowError("WF_NO_INITIAL", s"Initial step '${definition.initialStepId}' not found")
            )
          case Some(_) =>
            Right(
              WorkflowInstance(
                id = EntityId.random[WorkflowTag](),
                definitionId = definition.id,
                status = WorkflowStatus.Running,
                currentStepId = Some(definition.initialStepId),
                state = initial,
                history = Nil,
                createdAt = EffectiveInstant(now),
                updatedAt = EffectiveInstant(now)
              )
            )

    def advance(
        instance: WorkflowInstance[S],
        definition: WorkflowDefinition[S, F]
    ): F[DomainResult[WorkflowInstance[S]]] =
      instance.currentStepId match
        case None =>
          Sync[F].pure(
            DomainResult.raise(DomainError.WorkflowError("WF_NO_STEP", "No current step"))
          )
        case Some(stepId) =>
          definition.step(stepId) match
            case None =>
              Sync[F].pure(
                DomainResult.raise(DomainError.WorkflowError("WF_UNKNOWN_STEP", s"Step '$stepId' not found", Some(stepId)))
              )
            case Some(step) =>
              for
                started <- Sync[F].realTimeInstant
                result <- step.execute(instance.state)
                finished <- Sync[F].realTimeInstant
              yield result.flatMap: newState =>
                val exec = StepExecution(
                  stepId = stepId,
                  status = StepStatus.Completed,
                  startedAt = Some(EffectiveInstant(started)),
                  completedAt = Some(EffectiveInstant(finished))
                )
                if step.isTerminal then
                  Right(
                    instance.copy(
                      status = WorkflowStatus.Completed,
                      currentStepId = None,
                      state = newState,
                      history = instance.history :+ exec,
                      updatedAt = EffectiveInstant(finished)
                    )
                  )
                else
                  step.transitions.find(_.guard(newState)) match
                    case None =>
                      DomainResult.raise(
                        DomainError.WorkflowError(
                          "WF_NO_TRANSITION",
                          s"No transition matched from step '$stepId'",
                          Some(stepId)
                        )
                      )
                    case Some(tr) =>
                      Right(
                        instance.copy(
                          currentStepId = Some(tr.to),
                          state = newState,
                          history = instance.history :+ exec,
                          updatedAt = EffectiveInstant(finished)
                        )
                      )

    def cancel(instance: WorkflowInstance[S], reason: String): F[WorkflowInstance[S]] =
      Sync[F].realTimeInstant.map: now =>
        instance.copy(
          status = WorkflowStatus.Cancelled,
          history = instance.history :+ StepExecution(
            stepId = instance.currentStepId.getOrElse("cancelled"),
            status = StepStatus.Skipped,
            completedAt = Some(EffectiveInstant(now)),
            error = Some(reason)
          ),
          updatedAt = EffectiveInstant(now)
        )
