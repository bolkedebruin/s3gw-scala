package com.ing.ranger.conditions;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.ranger.plugin.conditionevaluator.RangerAbstractConditionEvaluator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ranger.plugin.policyengine.RangerAccessRequest;

import java.util.ArrayList;
import java.util.List;

public class IpMatcher extends RangerAbstractConditionEvaluator {
  private static final Log LOG = LogFactory.getLog(IpMatcher.class);
  private List<SubnetUtils.SubnetInfo> cidrs = new ArrayList<>();

  private boolean _allowAny;

  @Override
  public void init() {
    super.init();

    // NOTE: this evaluator does not use conditionDef!
    if (condition == null) {
      LOG.debug("init: null policy condition! Will match always!");
      _allowAny = true;
    } else if (CollectionUtils.isEmpty(condition.getValues())) {
      LOG.debug("init: empty conditions collection on policy condition!  Will match always!");
      _allowAny = true;
    } else if (condition.getValues().contains("*")) {
      _allowAny = true;
      LOG.debug("init: wildcard value found.  Will match always.");
    }

    for (String cidr : condition.getValues()) {
      LOG.debug("Adding cidr: " + cidr);
      try {
        SubnetUtils.SubnetInfo info = new SubnetUtils(cidr).getInfo();
        cidrs.add(info);
      } catch (IllegalArgumentException e) {
        LOG.warn("Skipping invalid cidr range: " + cidr);
      }
    }

  }

  @Override
  public boolean isMatched(final RangerAccessRequest request) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("==> com.ing.ranger.conditions.IpMatcher.isMatched(" + request + ")");
    }

    if (_allowAny) {
      LOG.debug("isMatched: allowAny flag is true.  Matched!");
      return true;
    }

    boolean clientIpOk = false;
    boolean remoteIpOk = false;

    for (SubnetUtils.SubnetInfo cidr : cidrs) {
      LOG.debug("client ip=" + request.getClientIPAddress() + " remote ip=" + request.getRemoteIPAddress() + " cidr=" + cidr.getNetworkAddress());
      if (cidr.isInRange(request.getClientIPAddress())) {
        clientIpOk = true;
      }
      if (cidr.isInRange(request.getRemoteIPAddress())) {
        remoteIpOk = true;
      }
      if (clientIpOk && remoteIpOk) {
        break;
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("==> com.ing.ranger.conditions.IpMatcher.isMatched: clientOk=" + clientIpOk + " remoteOk="+remoteIpOk);
    }

    if (!clientIpOk && !remoteIpOk) {
      return false;
    }

    if (request.getForwardedAddresses().size() < 1) {
      return true;
    }

    boolean fwdsOk = false;

    for (String fwd : request.getForwardedAddresses()) {
      for (SubnetUtils.SubnetInfo cidr : cidrs) {
        if (cidr.isInRange(fwd)) {
          fwdsOk = true;
          break;
        } else {
          fwdsOk = false;
        }
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("<== com.ing.ranger.conditions.IpMatcher.isMatched(" + request + ") " + fwdsOk);
    }

    return fwdsOk;
  }
}
