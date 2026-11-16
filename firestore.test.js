const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfile(uid) {
  return {
    userId: uid,
    displayName: "VoxelGhost",
    rankPoints: 1000,
    rankTier: "Bronze I",
    wins: 0,
    matchesPlayed: 0,
    totalKills: 0,
    totalDeaths: 0,
    totalHeadshots: 0,
    sensitivity: 1.0,
    soundEnabled: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validMatch(uid) {
  return {
    userId: uid,
    playerTeamScore: 4,
    enemyTeamScore: 2,
    won: true,
    kills: 9,
    deaths: 3,
    headshots: 4,
    damageDealt: 1850,
    rankDelta: 30,
    createdAt: serverTimestamp(),
  };
}

function validLeaderboard(uid) {
  return {
    userId: uid,
    displayName: "VoxelGhost",
    rankPoints: 1030,
    rankTier: "Silver I",
    wins: 1,
    totalKills: 9,
    updatedAt: serverTimestamp(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write profiles, matches, or leaderboard", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertFails(unauthDb.collection("leaderboard").limit(10).get());
});

test("Authenticated owner: can create and update own profile, cross-user read/write fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Cross-user access denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked", updatedAt: serverTimestamp() }));

  // Shadow field injection denied
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({ isAdmin: true, updatedAt: serverTimestamp() })
  );

  // Valid sensitivity update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      sensitivity: 1.4,
      soundEnabled: false,
      updatedAt: serverTimestamp(),
    })
  );
});

test("Match history: owner can create when profile exists, cannot update/delete, Bob cannot read", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  const matchRef = aliceDb.collection("users").doc(ALICE_UID).collection("matches").doc("match_1");
  await assertSucceeds(matchRef.set(validMatch(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("matches").get());

  // Append-only: update and delete must fail
  await assertFails(matchRef.update({ kills: 99 }));
  await assertFails(matchRef.delete());

  // Cross-user read/create must fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("matches").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("matches").doc("m2").set(validMatch(BOB_UID)));
});

test("Leaderboard: bounded query succeeds, unbounded list fails, cross-user write fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("leaderboard").doc(ALICE_UID).set(validLeaderboard(ALICE_UID)));
  await assertSucceeds(bobDb.collection("leaderboard").orderBy("rankPoints", "desc").limit(25).get());

  // Unbounded list without limit must fail
  await assertFails(bobDb.collection("leaderboard").get());

  // Cross-user spoofed write must fail
  await assertFails(bobDb.collection("leaderboard").doc(ALICE_UID).set(validLeaderboard(ALICE_UID)));
  await assertFails(bobDb.collection("leaderboard").doc(BOB_UID).set(validLeaderboard(ALICE_UID)));
});
