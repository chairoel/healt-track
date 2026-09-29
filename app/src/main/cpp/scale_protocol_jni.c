#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "scale_protocol.h"

#define JNI_PKG Java_com_mascill_healthtrack_data_protocol_NativeProtocol_

/* Helper to get bytes from jbyteArray */
static uint8_t *get_bytes(JNIEnv *env, jbyteArray arr, int *len) {
    *len = (*env)->GetArrayLength(env, arr);
    if (*len == 0) return NULL;
    uint8_t *buf = (uint8_t *)(*env)->GetByteArrayElements(env, arr, NULL);
    return buf;
}

static void release_bytes(JNIEnv *env, jbyteArray arr, uint8_t *buf) {
    if (buf) (*env)->ReleaseByteArrayElements(env, arr, (jbyte *)buf, JNI_ABORT);
}

/* ════════════════════════════════════════════════════════════
 *  Advertisement
 * ════════════════════════════════════════════════════════════ */

JNIEXPORT jbyteArray JNICALL
Java_com_mascill_healthtrack_data_protocol_NativeProtocol_nativeScalePayload(
        JNIEnv *env, jclass cls, jbyteArray record) {
    int len;
    uint8_t *buf = get_bytes(env, record, &len);
    if (!buf) return NULL;

    int offset, plen;
    int rc = sp_find_scale_payload(buf, len, &offset, &plen);
    if (rc != 0) {
        release_bytes(env, record, buf);
        return NULL;
    }

    jbyteArray result = (*env)->NewByteArray(env, plen);
    (*env)->SetByteArrayRegion(env, result, 0, plen, (jbyte *)(buf + offset));
    release_bytes(env, record, buf);
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_mascill_healthtrack_data_protocol_NativeProtocol_nativePayloadFromManufacturer(
        JNIEnv *env, jclass cls, jint companyId, jbyteArray remainder) {
    if (companyId < 0 || companyId > 0xFFFF) {
        jclass ex = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        (*env)->ThrowNew(env, ex, "manufacturer id does not fit in 16 bits");
        return NULL;
    }

    int rem_len;
    uint8_t *rem_buf = get_bytes(env, remainder, &rem_len);

    int total_len = 2 + rem_len;
    uint8_t *payload = (uint8_t *)malloc(total_len);
    payload[0] = (uint8_t)(companyId & 0xFF);
    payload[1] = (uint8_t)((companyId >> 8) & 0xFF);
    if (rem_buf && rem_len > 0) {
        memcpy(payload + 2, rem_buf, rem_len);
    }
    release_bytes(env, remainder, rem_buf);

    int first = sp_u8(payload[0]);
    if (first != SP_HEADER_CA && first != SP_HEADER_C0) {
        free(payload);
        return NULL;
    }

    jbyteArray result = (*env)->NewByteArray(env, total_len);
    (*env)->SetByteArrayRegion(env, result, 0, total_len, (jbyte *)payload);
    free(payload);
    return result;
}

/* ════════════════════════════════════════════════════════════
 *  Weight
 * ════════════════════════════════════════════════════════════ */

JNIEXPORT jdoubleArray JNICALL
Java_com_mascill_healthtrack_data_protocol_NativeProtocol_nativeWeightKilograms(
        JNIEnv *env, jclass cls,
        jint high, jint low, jint prop, jboolean cloud, jboolean cloudV4) {
    sp_weight_t w;
    sp_weight_kilograms(high, low, prop, cloud ? 1 : 0, cloudV4 ? 1 : 0, &w);

    jdoubleArray result = (*env)->NewDoubleArray(env, 7);
    jdouble vals[7] = {
        w.kg, (double)w.places, (double)w.unit_code,
        (double)w.raw, w.scaled, (double)w.high, (double)w.low
    };
    (*env)->SetDoubleArrayRegion(env, result, 0, 7, vals);
    return result;
}

/* ════════════════════════════════════════════════════════════
 *  Broadcast
 * ════════════════════════════════════════════════════════════ */

#define BR_SIZE 18

JNIEXPORT jdoubleArray JNICALL
Java_com_mascill_healthtrack_data_protocol_NativeProtocol_nativeParseBroadcast(
        JNIEnv *env, jclass cls, jbyteArray frame) {
    int len;
    uint8_t *buf = get_bytes(env, frame, &len);

    sp_broadcast_t br;
    int rc = sp_parse_broadcast(buf ? buf : (const uint8_t *)"", buf ? len : 0, &br);
    release_bytes(env, frame, buf);

    jdoubleArray result = (*env)->NewDoubleArray(env, BR_SIZE);
    jdouble vals[BR_SIZE];
    vals[0]  = (double)br.error_code;
    vals[1]  = (double)br.header;
    vals[2]  = (double)br.version;
    vals[3]  = (double)br.weight_kg;
    vals[4]  = (double)br.has_impedance;
    vals[5]  = (double)br.impedance_ohm;
    vals[6]  = (double)br.product_id;
    vals[7]  = (double)br.device_type;
    vals[8]  = (double)br.cmd_id;
    vals[9]  = (double)br.locked;
    vals[10] = (double)br.seq;
    vals[11] = (double)br.property;
    vals[12] = (double)br.scale_weight_mode;
    vals[13] = (double)br.weight.high;
    vals[14] = (double)br.weight.low;
    vals[15] = (double)br.weight.places;
    vals[16] = (double)br.weight.unit_code;
    vals[17] = br.weight.scaled;
    (*env)->SetDoubleArrayRegion(env, result, 0, BR_SIZE, vals);
    return result;
}

/* ════════════════════════════════════════════════════════════
 *  GATT
 * ════════════════════════════════════════════════════════════ */

#define GATT_SIZE 22

static jdoubleArray pack_gatt(JNIEnv *env, const sp_gatt_t *g) {
    jdoubleArray result = (*env)->NewDoubleArray(env, GATT_SIZE);
    jdouble vals[GATT_SIZE];
    vals[0]  = (double)g->error_code;
    vals[1]  = (double)g->version;
    vals[2]  = (double)g->has_command;
    vals[3]  = (double)g->command;
    vals[4]  = (double)g->lock_flag;
    vals[5]  = (double)g->property;
    vals[6]  = g->weight_kg;
    vals[7]  = (double)g->history;
    vals[8]  = g->comp.axunge;
    vals[9]  = g->comp.water;
    vals[10] = g->comp.muscle;
    vals[11] = g->comp.bmr;
    vals[12] = g->comp.visceral;
    vals[13] = g->comp.ori;
    vals[14] = g->comp.bone;
    vals[15] = (double)g->role_id;
    vals[16] = (double)g->has_weighing_date;
    vals[17] = (double)g->weighing_date_epoch;
    vals[18] = (double)g->weight.high;
    vals[19] = (double)g->weight.low;
    vals[20] = (double)g->weight.places;
    vals[21] = (double)g->weight.unit_code;
    (*env)->SetDoubleArrayRegion(env, result, 0, GATT_SIZE, vals);
    return result;
}

JNIEXPORT jdoubleArray JNICALL
Java_com_mascill_healthtrack_data_protocol_NativeProtocol_nativeParseGattV16(
        JNIEnv *env, jclass cls, jbyteArray frame) {
    int len;
    uint8_t *buf = get_bytes(env, frame, &len);
    sp_gatt_t g;
    sp_parse_gatt_v16(buf, len, &g);
    release_bytes(env, frame, buf);
    return pack_gatt(env, &g);
}

JNIEXPORT jdoubleArray JNICALL
Java_com_mascill_healthtrack_data_protocol_NativeProtocol_nativeParseGattV17Weight(
        JNIEnv *env, jclass cls, jbyteArray frame, jint command) {
    int len;
    uint8_t *buf = get_bytes(env, frame, &len);
    sp_gatt_t g;
    sp_parse_gatt_v17_weight(buf, len, command, &g);
    release_bytes(env, frame, buf);
    return pack_gatt(env, &g);
}

JNIEXPORT jdoubleArray JNICALL
Java_com_mascill_healthtrack_data_protocol_NativeProtocol_nativeParseGattAssembled(
        JNIEnv *env, jclass cls, jbyteArray body, jint command) {
    int len;
    uint8_t *buf = get_bytes(env, body, &len);
    sp_gatt_t g;
    sp_parse_gatt_assembled(buf, len, command, &g);
    release_bytes(env, body, buf);
    return pack_gatt(env, &g);
}

JNIEXPORT jdoubleArray JNICALL
Java_com_mascill_healthtrack_data_protocol_NativeProtocol_nativeParseGattRoles(
        JNIEnv *env, jclass cls, jbyteArray body) {
    int len;
    uint8_t *buf = get_bytes(env, body, &len);
    sp_confirmation_t conf;
    sp_parse_gatt_roles(buf, len, &conf);
    release_bytes(env, body, buf);

    int total = 11 + conf.role_count;
    jdoubleArray result = (*env)->NewDoubleArray(env, total);
    jdouble *vals = (jdouble *)malloc(total * sizeof(jdouble));
    vals[0]  = (double)conf.error_code;
    vals[1]  = (double)conf.version;
    vals[2]  = (double)conf.command;
    vals[3]  = (double)conf.property;
    vals[4]  = conf.weight_kg;
    vals[5]  = (double)conf.weight.high;
    vals[6]  = (double)conf.weight.low;
    vals[7]  = (double)conf.weight.places;
    vals[8]  = (double)conf.weight.unit_code;
    vals[9]  = conf.weight.scaled;
    vals[10] = (double)conf.role_count;
    for (int i = 0; i < conf.role_count; i++) {
        vals[11 + i] = (double)conf.roles[i];
    }
    (*env)->SetDoubleArrayRegion(env, result, 0, total, vals);
    free(vals);
    return result;
}

/* ════════════════════════════════════════════════════════════
 *  Utility JNI exports
 * ════════════════════════════════════════════════════════════ */

JNIEXPORT jintArray JNICALL
Java_com_mascill_healthtrack_data_protocol_NativeProtocol_nativeSplitPack(
        JNIEnv *env, jclass cls, jint raw) {
    int total, number;
    sp_split_pack(raw, &total, &number);
    jintArray result = (*env)->NewIntArray(env, 2);
    jint vals[2] = { total, number };
    (*env)->SetIntArrayRegion(env, result, 0, 2, vals);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_mascill_healthtrack_data_protocol_NativeProtocol_nativeJavaByte(
        JNIEnv *env, jclass cls, jint value) {
    return sp_java_byte(value);
}
