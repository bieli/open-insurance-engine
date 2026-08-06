package com.github.bieli.openinsuranceengine.policy

enum PolicyStatus:
  case Quote, Draft, Quoted, Bound, InForce, Cancelled, Expired, NonRenewed, Reinstated

object PolicyStatus:
  given CanEqual[PolicyStatus, PolicyStatus] = CanEqual.derived

enum JobType:
  case Submission, PolicyChange, Renewal, Cancellation, Reinstatement, Rewrite, Audit

object JobType:
  given CanEqual[JobType, JobType] = CanEqual.derived
