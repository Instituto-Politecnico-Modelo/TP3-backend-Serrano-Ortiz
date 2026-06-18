#!/usr/bin/env python3
"""
T050 — Seed SQL: 10.000 registros de auditoría con cadena SHA-256 válida.

Fórmula (debe coincidir exactamente con HashChainService.calcularHash):
  SHA-256(hashAnterior | entidad | idEntidad | accion | descripcion | timestamp | idUsuario)

Uso:
  python3 generate_auditoria_seed.py > auditoria_seed_10k.sql
"""
import hashlib
import random
from datetime import datetime, timedelta

GENESIS_HASH = hashlib.sha256("GENESIS-AUDITORIA-INSTITUCIONAL".encode()).hexdigest()

EVENTOS = [
    ("INSCRIPCION", "NOTA_CARGADA",          "Nota cargada por docente"),
    ("INSCRIPCION", "NOTA_CERRADA",           "Nota cerrada definitivamente"),
    ("INSCRIPCION", "NOTA_REABIERTA",         "Nota reabierta por administrador"),
    ("INSCRIPCION", "INSCRIPCION_CONFIRMADA", "Inscripción confirmada exitosamente"),
    ("INSCRIPCION", "INSCRIPCION_ENCOLADA",   "Inscripción encolada (cupo agotado)"),
    ("INSCRIPCION", "INSCRIPCION_CANCELADA",  "Inscripción cancelada por el alumno"),
    ("USUARIO",     "LOGIN_FALLIDO",          "Credenciales inválidas"),
    ("USUARIO",     "CUENTA_BLOQUEADA",       "Cuenta bloqueada por intentos fallidos"),
    ("USUARIO",     "USUARIO_CREADO",         "Usuario creado por administrador"),
    ("USUARIO",     "USUARIO_ELIMINADO",      "Usuario eliminado del sistema"),
]

USUARIOS = [
    (1, "juan.perez@estudiante.edu.ar"),
    (2, "maria.gonzalez@estudiante.edu.ar"),
    (3, "pedro.lopez@estudiante.edu.ar"),
    (4, "carlos.rodriguez@docente.edu.ar"),
    (5, "laura.fernandez@docente.edu.ar"),
    (6, "ana.martinez@admin.edu.ar"),
]

IPS = ["192.168.1.10", "192.168.1.20", "10.0.0.5", "172.16.0.3", "127.0.0.1"]

N = 10_000
BATCH = 500  # INSERT por lotes para mayor eficiencia en MySQL

def safe(value) -> str:
    """Equivale a safeStr() de HashChainService: null → 'null' (string literal)."""
    return "null" if value is None else str(value)

def calcular_hash(hash_anterior, entidad, id_entidad, accion, descripcion, timestamp, id_usuario) -> str:
    partes = [
        safe(hash_anterior),
        safe(entidad),
        safe(id_entidad),
        safe(accion),
        safe(descripcion),
        safe(timestamp),
        safe(id_usuario),
    ]
    entrada = "|".join(partes)
    return hashlib.sha256(entrada.encode()).hexdigest()

def escape_sql(s: str) -> str:
    return s.replace("'", "''")

def main():
    print("-- ============================================================")
    print("-- T050 — Seed de 10.000 registros de auditoría (cadena SHA-256 válida)")
    print("-- Generado con generate_auditoria_seed.py")
    print("-- GENESIS_HASH =", GENESIS_HASH)
    print("-- ============================================================")
    print()
    print("SET FOREIGN_KEY_CHECKS = 0;")
    print("SET autocommit = 0;")
    print()

    hash_anterior = GENESIS_HASH
    base_ts = datetime(2025, 1, 1, 8, 0, 0)

    rows = []
    for i in range(1, N + 1):
        entidad, accion, desc_base = random.choice(EVENTOS)
        id_entidad = random.randint(1, 200) if entidad == "INSCRIPCION" else None
        id_usuario, email = random.choice(USUARIOS)
        ip = random.choice(IPS)
        ts = base_ts + timedelta(seconds=i * 3 + random.randint(0, 2))
        ts_str = ts.strftime("%Y-%m-%dT%H:%M:%S")  # LocalDateTime.toString() sin ms cuando son 0

        descripcion = f"{desc_base} — registro #{i}"

        hash_actual = calcular_hash(
            hash_anterior, entidad, id_entidad, accion, descripcion, ts_str, id_usuario
        )

        rows.append((entidad, id_entidad, accion, id_usuario, email,
                     descripcion, ip, ts_str, hash_anterior, hash_actual))

        hash_anterior = hash_actual

        if len(rows) == BATCH or i == N:
            print("INSERT INTO auditoria")
            print("  (entidad, id_entidad, accion, id_usuario, email_usuario,")
            print("   descripcion, ip_origen, timestamp_evento, hash_anterior, hash_actual)")
            print("VALUES")
            val_strs = []
            for (ent, iden, acc, idu, eml, dsc, ipv, tse, ha, hac) in rows:
                id_ent_sql = "NULL" if iden is None else str(iden)
                id_usr_sql = "NULL" if idu is None else str(idu)
                val_strs.append(
                    f"  ('{escape_sql(ent)}', {id_ent_sql}, '{escape_sql(acc)}', "
                    f"{id_usr_sql}, '{escape_sql(eml)}', '{escape_sql(dsc)}', "
                    f"'{escape_sql(ipv)}', '{tse.replace('T', ' ')}', "
                    f"'{escape_sql(ha)}', '{escape_sql(hac)}')"
                )
            print(",\n".join(val_strs) + ";")
            print()
            rows = []

    print("COMMIT;")
    print("SET autocommit = 1;")
    print("SET FOREIGN_KEY_CHECKS = 1;")
    print()
    print(f"-- Último hash de la cadena: {hash_anterior}")
    print(f"-- Total registros: {N}")

if __name__ == "__main__":
    main()
