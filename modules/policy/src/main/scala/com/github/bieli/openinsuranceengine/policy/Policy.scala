package com.github.bieli.openinsuranceengine.policy

enum PolicyStatus:
  case Quote, Draft, Quoted, Bound, InForce, Cancelled, Expired, NonRenewed, Reinstated
