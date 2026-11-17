module.paths.push('/usr/local/lib/node_modules', '/usr/local/lib/node_modules/@firebase/rules-unit-testing/node_modules');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, getDoc, getDocs, setDoc, deleteDoc, collection, query, where, serverTimestamp, increment } = require('@firebase/firestore');
const fs = require('fs');
const { test, before, after, beforeEach } = require('node:test');

const PROJECT_ID = 'gen-lang-client-0170034643';
const DATABASE_ID = 'ai-studio-android-mototech-364d3daf-8123-4de3-b899-775d66bdcdfb';

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: '127.0.0.1',
      port: 8085,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

test('fichas: client can list and read published fichas, cannot read unpublished or write', async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore(DATABASE_ID);
    await setDoc(doc(adminDb, 'fichas', 'yamaha_fz-25_249_2022-2025'), {
      marca: 'Yamaha',
      modelo: 'FZ-25',
      cilindraje_cc: 249,
      anio_desde: 2022,
      anio_hasta: 2025,
      clave: 'yamaha|fz-25|249',
      estado: 'publicada',
    });
    await setDoc(doc(adminDb, 'fichas', 'yamaha_fz-25_249_borrador'), {
      marca: 'Yamaha',
      modelo: 'FZ-25',
      cilindraje_cc: 249,
      anio_desde: 2026,
      anio_hasta: 2026,
      clave: 'yamaha|fz-25|249',
      estado: 'borrador',
    });
  });

  const clientDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);

  // Querying with estado == 'publicada' succeeds
  const q = query(
    collection(clientDb, 'fichas'),
    where('clave', '==', 'yamaha|fz-25|249'),
    where('estado', '==', 'publicada')
  );
  await assertSucceeds(getDocs(q));

  // Querying without estado == 'publicada' is rejected by rules
  const qUnfiltered = query(
    collection(clientDb, 'fichas'),
    where('clave', '==', 'yamaha|fz-25|249')
  );
  await assertFails(getDocs(qUnfiltered));

  // Direct read of unpublished ficha fails
  await assertFails(getDoc(doc(clientDb, 'fichas', 'yamaha_fz-25_249_borrador')));

  // Client write to fichas fails
  await assertFails(
    setDoc(doc(clientDb, 'fichas', 'fake_ficha'), {
      marca: 'Yamaha',
      modelo: 'FZ-25',
      cilindraje_cc: 249,
      anio_desde: 2022,
      anio_hasta: 2025,
      clave: 'yamaha|fz-25|249',
      estado: 'publicada',
    })
  );
});

test('solicitudes: client can create with total=1 and increment by 1, cannot read or delete', async () => {
  const clientDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  const solicitudRef = doc(clientDb, 'solicitudes', 'yamaha_fz-25_249_2024');

  const payload = {
    marca: 'Yamaha',
    modelo: 'FZ-25',
    cilindraje_cc: 249,
    anio: 2024,
    clave: 'yamaha|fz-25|249',
    total: increment(1),
    ultima: serverTimestamp(),
  };

  // First solicitud creates document with total == 1
  await assertSucceeds(setDoc(solicitudRef, payload, { merge: true }));

  // Second solicitud increments total from 1 to 2
  await assertSucceeds(setDoc(solicitudRef, payload, { merge: true }));

  // Incrementing by more than 1 fails
  await assertFails(
    setDoc(
      solicitudRef,
      { ...payload, total: increment(5) },
      { merge: true }
    )
  );

  // Reading or deleting solicitudes from client fails
  await assertFails(getDoc(solicitudRef));
  await assertFails(deleteDoc(solicitudRef));
});

test('reportes: client can create with total=1, increment by 1, cannot tamper immutable fields or read/delete', async () => {
  const clientDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  const reporteRef = doc(clientDb, 'reportes', 'yamaha-fz-25-249-2022-2025__capacidad-aceite');

  const payload = {
    ficha_id: 'yamaha_fz-25_249_2022-2025',
    clave: 'yamaha|fz-25|249',
    marca: 'Yamaha',
    modelo: 'FZ-25',
    cilindraje_cc: 249,
    campo: 'Capacidad de aceite',
    valor_reportado: '1.0 L',
    motivo: 'Valor diferente al manual',
    total: increment(1),
    ultima: serverTimestamp(),
  };

  // First report creates document with total == 1
  await assertSucceeds(setDoc(reporteRef, payload, { merge: true }));

  // Second report increments total by 1 (motivo can vary)
  await assertSucceeds(
    setDoc(
      reporteRef,
      { ...payload, motivo: 'Otra versión/año' },
      { merge: true }
    )
  );

  // Incrementing by more than 1 fails
  await assertFails(
    setDoc(
      reporteRef,
      { ...payload, total: increment(3) },
      { merge: true }
    )
  );

  // Modifying immutable fields fails
  await assertFails(
    setDoc(
      reporteRef,
      { ...payload, campo: 'Otro campo' },
      { merge: true }
    )
  );

  // Extra fields fail strict schema check
  await assertFails(
    setDoc(
      doc(clientDb, 'reportes', 'extra_field_test'),
      { ...payload, extra: 'malicious' },
      { merge: true }
    )
  );

  // Oversized string fails length check
  await assertFails(
    setDoc(
      doc(clientDb, 'reportes', 'oversized_test'),
      { ...payload, motivo: 'a'.repeat(121) },
      { merge: true }
    )
  );

  // Reading or deleting reportes from client fails
  await assertFails(getDoc(reporteRef));
  await assertFails(deleteDoc(reporteRef));
});
