package ru.isupden.schedulingmodule.strategy;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.isupden.schedulingmodule.model.Task;


@RequiredArgsConstructor
@Component("fairness")
public class FairnessSchedulingStrategy implements UsageAwareStrategy {

    private final Map<String,Double> quotas;    // tenant↦quota
    private final double halfLifeSec;

    private static final class Usage {
        double value; long lastMs;
        Usage(double v,long ts){ value=v; lastMs=ts; }
    }
    private final Map<String,Usage> usage = new ConcurrentHashMap<>();

    /* ---- compare ---- */
    @Override public boolean canCompare(Task a, Task b) {
        return a.attr("tenantId", String.class) != null
                && b.attr("tenantId", String.class) != null;
    }
    @Override public int compare(Task a, Task b) {
        String ta = a.attr("tenantId", String.class);
        String tb = b.attr("tenantId", String.class);
        return Double.compare(share(ta), share(tb));
    }

    /* ---- usage accounting ---- */
    @Override
    public void recordUsage(String tenant,double cost,Instant at) {
        long now=at.toEpochMilli();
        usage.compute(tenant,(t,u)->u==null?new Usage(cost,now)
                :new Usage(decay(u,now)+cost, now));
    }
    @Override
    public void preprocess(java.util.Queue<Task> q, Instant now) {
        long ts = now.toEpochMilli();
        usage.replaceAll((t,u)->new Usage(decay(u,ts), ts));
    }

    /* ---- helpers ---- */
    private double share(String tenant){
        Usage u = usage.getOrDefault(tenant,new Usage(0,0));
        double quota = quotas.getOrDefault(tenant,1.0);
        return u.value / quota;
    }
    private double decay(Usage u,long nowMs){
        double dt = (nowMs-u.lastMs)/1000.0;
        if(dt<=0) return u.value;
        double factor = Math.pow(0.5, dt/halfLifeSec);
        return u.value*factor;
    }
}
