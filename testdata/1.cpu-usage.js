import { check, sleep } from "k6";
import remote from "k6/x/remotewrite";

export let options = {
  iterations: 500
};

const client = new remote.Client({
  url: "http://localhost:9090/api/v1/write",
});

let iteration = 0;
let failoverCooldown = 0;
let activePrimary = "db-primary"; // Track which DB is active

const servers = [
  { name: "db-primary", role: "database" },
  { name: "db-replica-1", role: "database" },
  { name: "db-replica-2", role: "database" },
  { name: "frontend-1", role: "frontend" },
  { name: "frontend-2", role: "frontend" },
  { name: "frontend-3", role: "frontend" },
  { name: "backend-1", role: "backend" },
  { name: "backend-2", role: "backend" },
  { name: "backend-3", role: "backend" },
];

export default function() {
  const time = iteration / 100;

  // Generate metrics
  let primaryLoad = 0;
  const metrics = servers.map(server => {
    const isActive = (server.role === "database" && server.name === activePrimary);
    const value = generateMetric(server, isActive, time);

    if (isActive) {
      primaryLoad = value;
    }

    return { server, value, isActive };
  });

  // Trigger failover if primary overloaded
  if (primaryLoad >= 96 && failoverCooldown === 0) {
    performFailover();
    failoverCooldown = 50;
  }

  if (failoverCooldown > 0) failoverCooldown--;

  // Send all metrics
  metrics.forEach(({ server, value, isActive }) => {
    sendMetricData(server, value);

    // Send separate metric for database active/passive state
    if (server.role === "database") {
      sendStateMetric(server, isActive ? 1 : 0);
    }
  });

  iteration++;
  sleep(0.1);
}

function performFailover() {
  const dbServers = servers.filter(s => s.role === "database");
  const passives = dbServers.filter(s => s.name !== activePrimary);

  if (passives.length > 0) {
    const newActive = passives[0];
    activePrimary = newActive.name;
    console.log(`DB failover: ${activePrimary} → passive, ${newActive.name} → active`);
  }
}

function generateMetric(server, isActive, time) {
  let value;

  switch (server.role) {
    case "database":
      if (isActive) {
        // Active DB: High load with trend toward 100%
        const base = 80;
        const wave = 8 * Math.sin(time * Math.PI * 6);
        const spike = (Math.random() > 0.85) ? 10 : 0;
        const noise = (Math.random() - 0.5) * 5;
        value = base + wave + spike + noise;
      } else {
        // Passive DB: Low load
        const base = 20;
        const noise = (Math.random() - 0.5) * 6;
        value = base + noise;
      }
      break;

    case "frontend":
      const trafficWave = 30 + 40 * Math.sin(time * Math.PI * 2 - Math.PI / 2);
      const burst = (Math.random() > 0.92) ? 20 : 0;
      const variance = normalRandom() * 10;
      value = trafficWave + burst + variance;
      break;

    case "backend":
      const base = 45 + 15 * Math.sin(time * Math.PI * 3);
      const jobProcessing = (Math.random() > 0.88) ? 30 : 0;
      const noise = normalRandom() * 8;
      value = base + jobProcessing + noise;
      break;

    default:
      value = 50;
  }

  return Math.floor(Math.max(0, Math.min(100, value)));
}

/// Box-Muller transform for transforming two uniform distributions
/// into one random
function normalRandom() {
  const u1 = Math.random();
  const u2 = Math.random();
  return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
}

function sendMetricData(server, value) {
  const res = client.store([
    {
      labels: [
        { name: "__name__", value: "cpu_usage" },
        { name: "job", value: "exporter" },
        { name: "instance", value: server.name },
        { name: "role", value: server.role },
      ],
      samples: [{ value: value }],
    },
  ]);
  check(res, {
    "is status 204": (r) => r.status === 204,
  });
}

function sendStateMetric(server, isActive) {
  client.store([
    {
      labels: [
        { name: "__name__", value: "db_is_active" },
        { name: "job", value: "exporter" },
        { name: "instance", value: server.name },
        { name: "role", value: "database" },
      ],
      samples: [{ value: isActive }],
    },
  ]);
}
