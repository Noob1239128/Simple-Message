const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_google_uid";
const BOB_UID = "bob_google_uid";
const ADMIN_UID = "admin_google_uid";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("accounts").doc("alice").get());
  await assertFails(unauthDb.collection("conversations").where("participantUids", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("calls").where("participantUids", "array-contains", ALICE_UID).get());
  await assertFails(
    unauthDb.collection("accounts").doc("alice").set({
      ownerUid: ALICE_UID,
      nickname: "Alice",
      nicknameLower: "alice",
      role: "USER",
      status: "ACTIVE",
      online: true,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Account registration, credential isolation, role escalation prevention, and profile update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  // Valid account create (no @DocumentId `id` field)
  await assertSucceeds(
    aliceDb.collection("accounts").doc("alice_99").set({
      ownerUid: ALICE_UID,
      nickname: "Alice_99",
      nicknameLower: "alice_99",
      role: "USER",
      status: "ACTIVE",
      online: true,
      bio: "Hallo PulseChat!",
      lastSeenAt: past,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Isolated credentials subcollection create
  await assertSucceeds(
    aliceDb.collection("accounts").doc("alice_99").collection("credentials").doc("auth").set({
      ownerUid: ALICE_UID,
      passwordHash: "a".repeat(64),
      salt: "salt_12345678",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Reject Ghost Field / @DocumentId `id` injection
  await assertFails(
    aliceDb.collection("accounts").doc("alice_ghost").set({
      id: "alice_ghost",
      ownerUid: ALICE_UID,
      nickname: "AliceGhost",
      nicknameLower: "alice_ghost",
      role: "USER",
      status: "ACTIVE",
      online: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Reject self-assigned non-USER role
  await assertFails(
    aliceDb.collection("accounts").doc("alice_admin").set({
      ownerUid: ALICE_UID,
      nickname: "AliceAdmin",
      nicknameLower: "alice_admin",
      role: "OWNER",
      status: "ACTIVE",
      online: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Bob can look up Alice by exact nickname (no PII / password hash exposed)
  await assertSucceeds(bobDb.collection("accounts").doc("alice_99").get());
  // Bob CANNOT read Alice's isolated password credentials
  await assertFails(bobDb.collection("accounts").doc("alice_99").collection("credentials").doc("auth").get());
  // Bob CANNOT update or delete Alice's account
  await assertFails(bobDb.collection("accounts").doc("alice_99").update({ online: false, updatedAt: new Date(Date.now() - 500) }));
  await assertFails(bobDb.collection("accounts").doc("alice_99").delete());

  // Unfiltered accounts list fails for non-admin; scoped query succeeds
  await assertFails(aliceDb.collection("accounts").get());
  await assertSucceeds(aliceDb.collection("accounts").where("ownerUid", "==", ALICE_UID).get());
});

test("OwnerConfig is strictly scoped to request.auth.uid", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("owner_configs").doc(ALICE_UID).set({
      ownerUid: ALICE_UID,
      username: "OwnerMax",
      usernameLower: "ownermax",
      passwordHash: "b".repeat(64),
      salt: "salt_owner_1234",
      githubRepo: "max/PulseChat",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("owner_configs").doc(ALICE_UID).update({
      githubRepo: "max/PulseChat-Android",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(bobDb.collection("owner_configs").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("owner_configs").doc(ALICE_UID).set({
    ownerUid: BOB_UID,
    username: "Hijack",
    usernameLower: "hijack",
    passwordHash: "c".repeat(64),
    salt: "salt_hijack_12",
    createdAt: past,
    updatedAt: past,
  }));
});

test("Conversations & Messages: participant scoping, voice/file bounds, and query alignment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);
  const convId = "alice_99__bob_77";

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      ownerUid: ALICE_UID,
      participantNicknames: ["alice_99", "bob_77"],
      participantUids: [ALICE_UID, BOB_UID],
      lastMessagePreview: "Hallo Bob!",
      lastMessageType: "TEXT",
      lastSenderNickname: "alice_99",
      messageCount: 1,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Text message
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").set({
      ownerUid: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      senderNickname: "alice_99",
      recipientNickname: "bob_77",
      type: "TEXT",
      textContent: "Hallo Bob!",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Voice message from Bob
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("msg_2").set({
      ownerUid: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      senderNickname: "bob_77",
      recipientNickname: "alice_99",
      type: "VOICE",
      voiceDurationMs: 4200,
      voiceBase64: "AAAA",
      createdAt: past,
      updatedAt: past,
    })
  );

  // File message from Alice
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_3").set({
      ownerUid: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      senderNickname: "alice_99",
      recipientNickname: "bob_77",
      type: "FILE",
      fileName: "bericht.pdf",
      fileMimeType: "application/pdf",
      fileSizeBytes: 12040,
      fileBase64: "JVBERi0=",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_future").set({
      ownerUid: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      senderNickname: "alice_99",
      recipientNickname: "bob_77",
      type: "TEXT",
      textContent: "Future",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Scoped queries succeed; unfiltered queries fail
  await assertSucceeds(aliceDb.collection("conversations").where("participantUids", "array-contains", ALICE_UID).get());
  await assertSucceeds(bobDb.collection("conversations").doc(convId).collection("messages").where("participantUids", "array-contains", BOB_UID).get());
  await assertFails(bobDb.collection("conversations").get());
  await assertFails(bobDb.collection("conversations").doc(convId).collection("messages").get());
});

test("Calls: ringing, active, terminal state locking, and admin global oversight", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      ownerUid: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      callerNickname: "alice_99",
      recipientNickname: "bob_77",
      callType: "AUDIO",
      status: "RINGING",
      durationSec: 0,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Bob accepts call
  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "ACTIVE",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Alice ends call
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").update({
      status: "ENDED",
      durationSec: 42,
      endedAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state lock: cannot reopen ENDED call
  await assertFails(
    aliceDb.collection("calls").doc("call_1").update({
      status: "ACTIVE",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Seed admin and verify global list access for Owner/Admin
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("admins").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      createdAt: past,
    });
  });

  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertSucceeds(adminDb.collection("accounts").get());
  await assertSucceeds(adminDb.collection("conversations").get());
  await assertSucceeds(adminDb.collection("calls").get());
});
