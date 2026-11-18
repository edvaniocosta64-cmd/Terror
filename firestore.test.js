const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validSavePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    slotName: "Slot 1",
    chapterTitle: "Dentro da Casa",
    storyPhase: "INSIDE_MYSTERY",
    playerX: 0.0,
    playerY: 1.65,
    playerZ: -2.0,
    playerYaw: 0.0,
    health: 100,
    battery: 85,
    flashlightOn: true,
    unlockedDoors: ["front_door"],
    collectedItems: ["lantern"],
    inventoryItems: ["lantern"],
    readNotes: ["note_hall"],
    playTimeSeconds: 45,
    createdAt: now,
    updatedAt: now,
    ...overrides,
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

test("Unauthenticated user cannot read or write saves", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("saves").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("saves").doc("slot_1").set(validSavePayload(ALICE_UID))
  );
});

test("Owner can create, list, update, and delete valid save slots", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("saves");

  // Empty list query succeeds
  await assertSucceeds(col.get());

  // Valid create succeeds (NO @DocumentId `id` field in payload)
  const payload = validSavePayload(ALICE_UID);
  await assertSucceeds(col.doc("slot_1").set(payload));

  // List after create succeeds
  await assertSucceeds(col.get());

  // Partial update preserving createdAt and userId succeeds
  await assertSucceeds(
    col.doc("slot_1").update({
      chapterTitle: "Perseguição da Vovó",
      storyPhase: "GRANNYS_PURSUIT",
      health: 75,
      playTimeSeconds: 120,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Delete succeeds
  await assertSucceeds(col.doc("slot_1").delete());
});

test("Cross-user read, write, and list are rejected", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore()
      .collection("users").doc(BOB_UID).collection("saves").doc("slot_1")
      .set(validSavePayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("saves").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("saves").doc("slot_1").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("saves").doc("slot_1").update({ health: 10 })
  );
});

test("Rejects @DocumentId id field, immutable createdAt change, invalid enum, and ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("saves");

  // Reject payload containing redundant `id` field
  await assertFails(col.doc("slot_bad_id").set(validSavePayload(ALICE_UID, { id: "slot_bad_id" })));

  // Reject invalid storyPhase enum
  await assertFails(col.doc("slot_bad_enum").set(validSavePayload(ALICE_UID, { storyPhase: "GOD_MODE" })));

  // Reject ghost field
  await assertFails(col.doc("slot_ghost").set(validSavePayload(ALICE_UID, { isAdmin: true })));

  // Reject future timestamp
  await assertFails(
    col.doc("slot_future").set(validSavePayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) }))
  );

  // Create valid doc then reject createdAt mutation on update
  await assertSucceeds(col.doc("slot_1").set(validSavePayload(ALICE_UID)));
  await assertFails(
    col.doc("slot_1").update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 100),
    })
  );
});
