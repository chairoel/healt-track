#include "scale_protocol.h"
#include <math.h>
#include <string.h>

/* ════════════════════════════════════════════════════════════
 *  Utility functions
 * ════════════════════════════════════════════════════════════ */

int sp_u8(uint8_t b) {
    return (int)(b & 0xFF);
}

int sp_java_byte(int value) {
    int u = value & 0xFF;
    return (u >= 128) ? (u - 256) : u;
}

int sp_bytes_to_int(const uint8_t *data, int len) {
    if (len <= 0) return 0;
    int value = 0;
    for (int i = 0; i < len; i++) {
        value = (value << 8) | sp_u8(data[i]);
    }
    return value;
}

int sp_bytes_to_int_safe(const uint8_t *data, int len) {
    if (len <= 0) return 0;
    int64_t value = 0;
    for (int i = 0; i < len; i++) {
        value = (value << 8) | (int64_t)sp_u8(data[i]);
    }
    if (value > 0x7FFFFFFF) return 0;
    return (int)value;
}

int sp_slice_int(const uint8_t *frame, int frame_len, int start, int size) {
    if (size <= 0 || frame_len < start + size) return 0;
    return sp_bytes_to_int_safe(frame + start, size);
}

void sp_split_pack(int raw, int *total, int *number) {
    int signed_val = sp_java_byte(raw);
    int low = sp_java_byte((signed_val << 4) & 0xFF);
    *total  = sp_java_byte(low >> 4);
    *number = sp_java_byte(signed_val >> 4);
}

double sp_f32(double value) {
    return (double)((float)value);
}

double sp_round_half_up(double value, int places, int as_f32) {
    double factor = 1.0;
    for (int i = 0; i < places; i++) factor *= 10.0;
    double number = floor(value * factor + 0.5) / factor;
    return as_f32 ? sp_f32(number) : number;
}

double sp_stored_weight(double kg) {
    return (kg * 10.0) / 10.0;
}

/* ════════════════════════════════════════════════════════════
 *  Bit helpers (broadcast)
 * ════════════════════════════════════════════════════════════ */

static void bits_str(int value, char out[9]) {
    int byte_val = value & 0xFF;
    for (int shift = 7; shift >= 0; shift--) {
        out[7 - shift] = ((byte_val >> shift) & 1) ? '1' : '0';
    }
    out[8] = '\0';
}

static int unit_code(int prop) {
    char b[9];
    bits_str(prop, b);
    if (b[3] == '0' && b[4] == '1') return SP_UNIT_JIN;
    if (b[3] == '1' && b[4] == '0') return SP_UNIT_LB;
    if (b[3] == '1' && b[4] == '1') return SP_UNIT_ST;
    return SP_UNIT_KG;
}

static int digit_places(int prop, int cloud, int cloud_v4) {
    char b[9];
    bits_str(prop, b);
    if (cloud_v4) {
        if (b[1] == '0' && b[2] == '1') return 0;
        if (b[1] == '1' && b[2] == '0') return 1;
        return 2;
    }
    char p0 = b[5], p1 = b[6];
    if (cloud) {
        if (p0 == '0' && p1 == '1') return 0;
        if (p0 == '1' && p1 == '0') return 1;
        return 2;
    }
    if (p0 == '0' && p1 == '1') return 0;
    if (p0 == '1' && p1 == '0') return 2;
    return 1;
}

static int lock_bit(int prop) {
    char b[9];
    bits_str(prop, b);
    return b[7] == '1' ? 1 : 0;
}

static int device_type_v2(int prop) {
    char b[9];
    bits_str(prop, b);
    return b[2] == '0' ? 0 : 2;
}

static double div10(int raw) {
    return sp_f32(sp_f32((double)raw) / sp_f32(10.0));
}

static const double LB_PER_KG = (double)0.4535924f;

/* ════════════════════════════════════════════════════════════
 *  Weight calculation
 * ════════════════════════════════════════════════════════════ */

int sp_weight_kilograms(int high, int low, int prop,
                        int cloud, int cloud_v4,
                        sp_weight_t *out) {
    int places = digit_places(prop, cloud, cloud_v4);
    uint8_t buf[2] = { (uint8_t)(high & 0xFF), (uint8_t)(low & 0xFF) };
    int raw = sp_bytes_to_int(buf, 2);

    double scaled;
    switch (places) {
        case 0:  scaled = sp_f32((double)raw); break;
        case 1:  scaled = sp_f32(sp_f32((double)raw) / sp_f32(10.0)); break;
        default: scaled = sp_f32(sp_f32((double)raw) / sp_f32(100.0)); break;
    }
    scaled = sp_round_half_up(scaled, places, 1);

    int uc = unit_code(prop);

    double kg;
    switch (uc) {
        case SP_UNIT_JIN:
            kg = sp_f32(scaled * sp_f32(0.5));
            break;
        case SP_UNIT_LB:
            kg = sp_f32(scaled * LB_PER_KG);
            break;
        case SP_UNIT_ST: {
            int stones = high & 0xFF;
            double pounds_f = sp_f32((double)(low & 0xFF) / 10.0);
            double total_lb = (double)(stones * 14) + pounds_f;
            kg = sp_round_half_up(sp_f32(sp_f32(total_lb) * LB_PER_KG), 3, 1);
            break;
        }
        default:
            kg = scaled;
            break;
    }
    kg = sp_round_half_up(kg, places, 0);

    out->kg = kg;
    out->scaled = scaled;
    out->places = places;
    out->unit_code = uc;
    out->raw = raw;
    out->high = high;
    out->low = low;
    return SP_OK;
}

/* ════════════════════════════════════════════════════════════
 *  Composition functions
 * ════════════════════════════════════════════════════════════ */

double sp_set_axunge(int raw) {
    if (raw >= 65535) return 0.0;
    double v = raw / 10.0;
    if (v >= 5.0 && v <= 45.0) return v;
    return 0.0;
}

double sp_set_water(int raw) {
    double v = raw / 10.0;
    if (v >= 100.0 || v < 20.0 || v > 85.0) return 0.0;
    return v;
}

double sp_set_muscle(int raw) {
    double v = raw / 10.0;
    if (v >= 100.0) return 0.0;
    return v;
}

double sp_set_bmr(int raw) {
    if (raw >= 65535) return 0.0;
    if (raw >= 5000) return raw / 10.0;
    return (double)raw;
}

void sp_set_visceral(int raw, double *shown, double *ori) {
    double s = raw / 10.0;
    if (s >= 100.0 || s > 59.0) s = 0.0;
    *shown = s;
    *ori = (double)raw;
}

double sp_set_bone(int raw) {
    if (raw >= 65535) return 0.0;
    double v = raw / 10.0;
    if (v >= 1.0) {
        if (v <= 4.0) return v;
        return 4.0;
    }
    return 0.0;
}

double sp_recalculate_muscle(double muscle, double weight_kg) {
    double ratio;
    if (weight_kg == 0.0 && muscle == 0.0) {
        ratio = 0.0 / 0.0; /* NaN */
    } else if (weight_kg == 0.0) {
        ratio = (muscle > 0.0) ? (1.0 / 0.0) : (-1.0 / 0.0);
    } else {
        ratio = (muscle / weight_kg) * 100.0;
    }

    double value;
    if (ratio >= 50.0) {
        value = (muscle >= 50.0) ? 50.0 : muscle;
    } else {
        value = ratio;
    }

    int raw_m;
    if (isnan(value)) {
        raw_m = 0;
    } else if (isinf(value)) {
        return -1.0; /* signal ArithmeticException */
    } else {
        raw_m = (int)(value * 10.0);
    }
    return sp_set_muscle(raw_m);
}

void sp_composition(const uint8_t *frame, int frame_len, sp_composition_t *out) {
    double visceral, ori;
    sp_set_visceral(sp_slice_int(frame, frame_len, 15, 2), &visceral, &ori);
    out->axunge  = sp_set_axunge(sp_slice_int(frame, frame_len, 7, 2));
    out->water   = sp_set_water(sp_slice_int(frame, frame_len, 9, 2));
    out->muscle  = sp_set_muscle(sp_slice_int(frame, frame_len, 11, 2));
    out->bmr     = sp_set_bmr(sp_slice_int(frame, frame_len, 13, 2));
    out->visceral = visceral;
    out->ori     = ori;
    out->bone    = sp_set_bone(sp_slice_int(frame, frame_len, 17, 1));
}

/* ════════════════════════════════════════════════════════════
 *  Advertisement
 * ════════════════════════════════════════════════════════════ */

static int is_scale_header(int byte_val) {
    return byte_val == SP_HEADER_CA || byte_val == SP_HEADER_C0;
}

int sp_find_scale_payload(const uint8_t *record, int record_len,
                          int *payload_offset, int *payload_len) {
    int index = 0;
    while (index < record_len) {
        int length = sp_u8(record[index]);
        index++;
        if (length == 0 || index + length > record_len) {
            return -1;
        }
        const uint8_t *block = record + index;
        int block_len = length;
        index += length;

        if (block_len == 0 || sp_u8(block[0]) != 0xFF || block_len < 2) {
            continue;
        }
        const uint8_t *payload = block + 1;
        int plen = block_len - 1;
        if (is_scale_header(sp_u8(payload[0]))) {
            *payload_offset = (int)(payload - record);
            *payload_len = plen;
            return 0;
        }
    }
    return -1;
}

int sp_check_manufacturer_payload(int company_id,
                                  const uint8_t *remainder, int rem_len,
                                  int *is_scale) {
    if (company_id < 0 || company_id > 0xFFFF) return -1;
    uint8_t first = (uint8_t)(company_id & 0xFF);
    *is_scale = is_scale_header(sp_u8(first)) ? 1 : 0;
    return 0;
}

/* ════════════════════════════════════════════════════════════
 *  Broadcast parsing helpers
 * ════════════════════════════════════════════════════════════ */

static int need_broadcast(int frame_len, int size) {
    return frame_len >= size;
}

static void fill_weight(sp_broadcast_t *out, int high, int low, int prop,
                        int cloud, int cloud_v4) {
    sp_weight_t w;
    sp_weight_kilograms(high, low, prop, cloud, cloud_v4, &w);
    out->weight_kg = (float)w.kg;
    out->weight = w;
    out->scale_weight_mode = SP_SW_TEXT;
}

static int cloud_property(int prop) {
    int uc = unit_code(prop);
    int places = digit_places(prop, 1, 0);
    int prefix;
    switch (uc) {
        case SP_UNIT_JIN: prefix = 1;  break; /* 00001 */
        case SP_UNIT_LB:  prefix = 2;  break; /* 00010 */
        case SP_UNIT_ST:  prefix = 3;  break; /* 00011 */
        default:          prefix = 0;  break; /* 00000 */
    }
    int suffix;
    switch (places) {
        case 1:  suffix = 1; break; /* 001 */
        case 2:  suffix = 5; break; /* 101 */
        default: suffix = 3; break; /* 011 */
    }
    return (prefix << 3) | suffix;
}

/* ── parseCaLegacy ── */
static int parse_ca_legacy(const uint8_t *frame, int len, sp_broadcast_t *out) {
    if (!need_broadcast(len, 5)) return SP_ERR_SHORT;
    int version = sp_u8(frame[1]);
    int device_type = 0;
    int prop = 0;
    int cmd_id = 0;

    if (version == 16) {
        device_type = sp_u8(frame[3]);
        prop = sp_u8(frame[4]);
        cmd_id = prop;
    } else if (version == 17) {
        prop = sp_u8(frame[3]);
        device_type = sp_u8(frame[4]);
    }

    out->version = version;

    if (device_type != 0) {
        if (!need_broadcast(len, 11)) return SP_ERR_SHORT;
        fill_weight(out, sp_u8(frame[5]), sp_u8(frame[6]), prop, 0, 0);
        out->device_type = device_type;
        out->cmd_id = lock_bit(prop) ? 1 : 0;
        out->property = prop & 0xFF;
        out->has_impedance = 0;
        out->product_id = sp_bytes_to_int(frame + 7, 4);
        out->seq = 0;
        out->locked = lock_bit(prop);
        return SP_OK;
    }

    if (version == 16) {
        if (!need_broadcast(len, 10)) return SP_ERR_SHORT;
        out->device_type = 0;
        out->cmd_id = cmd_id;
        out->property = prop & 0xFF;
        out->weight_kg = (float)div10(sp_bytes_to_int(frame + 8, 2));
        out->scale_weight_mode = SP_SW_NULL;
        out->has_impedance = 0;
        out->product_id = 0;
        out->seq = 0;
        out->locked = lock_bit(prop);
        return SP_OK;
    }

    if (version != 17) {
        out->device_type = 0;
        out->cmd_id = 0;
        out->property = 0;
        out->weight_kg = 0.0f;
        out->scale_weight_mode = SP_SW_NULL;
        out->has_impedance = 0;
        out->product_id = 0;
        out->seq = 0;
        out->locked = 0;
        return SP_OK;
    }

    if (!need_broadcast(len, 11)) return SP_ERR_SHORT;
    fill_weight(out, sp_u8(frame[5]), sp_u8(frame[6]), prop, 0, 0);
    out->device_type = 0;
    out->cmd_id = lock_bit(prop) ? 1 : 0;
    out->property = prop & 0xFF;
    out->has_impedance = 0;
    out->product_id = sp_bytes_to_int(frame + 7, 4);
    out->seq = 0;
    out->locked = lock_bit(prop);
    return SP_OK;
}

/* ── parseCaV20 ── */
static int parse_ca_v20(const uint8_t *frame, int len, sp_broadcast_t *out) {
    if (!need_broadcast(len, 14)) return SP_ERR_SHORT;
    int prop = sp_u8(frame[8]);
    int locked = lock_bit(prop);
    int dt = (sp_u8(frame[7]) == 0) ? 0 : 2;

    out->version = 32;
    out->device_type = dt;
    out->property = prop & 0xFF;
    out->product_id = sp_bytes_to_int(frame + 3, 4);

    if (locked) {
        fill_weight(out, sp_u8(frame[10]), sp_u8(frame[11]), prop, 0, 0);
        out->impedance_ohm = (float)div10(sp_bytes_to_int(frame + 12, 2));
        out->has_impedance = 1;
        out->seq = sp_u8(frame[9]);
        out->cmd_id = 1;
        out->locked = 1;
    } else {
        out->weight_kg = 0.0f;
        out->scale_weight_mode = SP_SW_EMPTY;
        out->impedance_ohm = 0.0f;
        out->has_impedance = 1;
        out->seq = 0;
        out->cmd_id = 0;
        out->locked = 0;
    }
    return SP_OK;
}

/* ── parseCa ── */
static int parse_ca(const uint8_t *frame, int len, sp_broadcast_t *out) {
    if (!need_broadcast(len, 2)) return SP_ERR_SHORT;
    if (sp_u8(frame[1]) == 32) {
        return parse_ca_v20(frame, len, out);
    }
    return parse_ca_legacy(frame, len, out);
}

/* ── parseC0 ── */
static int parse_c0(const uint8_t *frame, int len, sp_broadcast_t *out) {
    if (!need_broadcast(len, 9)) return SP_ERR_SHORT;
    if (sp_u8(frame[4]) & 0x80) {
        return SP_ERR_ENCRYPTED;
    }
    int prop = sp_u8(frame[8]);
    fill_weight(out, sp_u8(frame[2]), sp_u8(frame[3]), prop, 0, 0);
    out->version = 1;
    out->device_type = device_type_v2(prop);
    out->cmd_id = lock_bit(prop) ? 1 : 0;
    out->property = prop & 0xFF;
    out->impedance_ohm = (float)div10(sp_bytes_to_int(frame + 4, 2));
    out->has_impedance = 1;
    out->product_id = sp_bytes_to_int(frame + 6, 2);
    out->seq = sp_u8(frame[1]);
    out->locked = lock_bit(prop);
    return SP_OK;
}

/* ── parseFf ── */
static int parse_ff(const uint8_t *frame, int len, sp_broadcast_t *out) {
    if (!need_broadcast(len, 10)) return SP_ERR_SHORT;
    int version = sp_u8(frame[2]);
    int prop = sp_u8(frame[3]);
    int product = sp_bytes_to_int(frame + 6, 4);

    uint8_t magic[] = { 0xFA, 0xFB, 0xFC, 0xFD };
    if (len >= 10 && memcmp(frame + 6, magic, 4) == 0) {
        product = 997264800;
        prop = 4;
    } else if (product == 1104000001) {
        prop = 4;
    }

    int cloud_v4 = (version == 16 || version == 17) ? 1 : 0;
    int cloud = cloud_v4 ? 0 : 1;
    fill_weight(out, sp_u8(frame[5]), sp_u8(frame[4]), prop, cloud, cloud_v4);

    out->version = version;
    out->device_type = 1;
    out->cmd_id = 0;
    out->property = cloud_property(prop);
    out->has_impedance = 0;
    out->product_id = product;
    out->seq = 0;
    out->locked = 0;
    return SP_OK;
}

/* ── parseBroadcast ── */
int sp_parse_broadcast(const uint8_t *frame, int len, sp_broadcast_t *out) {
    memset(out, 0, sizeof(sp_broadcast_t));
    if (len <= 0) {
        out->error_code = SP_ERR_EMPTY;
        return SP_ERR_EMPTY;
    }
    out->header = sp_u8(frame[0]);
    int rc;
    switch (sp_u8(frame[0])) {
        case SP_HEADER_CA: rc = parse_ca(frame, len, out); break;
        case SP_HEADER_FF: rc = parse_ff(frame, len, out); break;
        case SP_HEADER_C0: rc = parse_c0(frame, len, out); break;
        default: rc = SP_ERR_UNKNOWN; break;
    }
    out->error_code = rc;
    return rc;
}

/* ════════════════════════════════════════════════════════════
 *  GATT parsing
 * ════════════════════════════════════════════════════════════ */

static void gatt_zero(sp_gatt_t *out) {
    memset(out, 0, sizeof(sp_gatt_t));
}

int sp_parse_gatt_v16(const uint8_t *frame, int len, sp_gatt_t *out) {
    gatt_zero(out);
    if (len < 7) { out->error_code = SP_ERR_FORMAT; return SP_ERR_FORMAT; }

    int prop = sp_u8(frame[4]);
    int lf = prop & 1;
    sp_weight_t w;
    sp_weight_kilograms(sp_u8(frame[5]), sp_u8(frame[6]), prop, 0, 0, &w);

    if (lf > 0) {
        sp_composition(frame, len, &out->comp);
    }

    out->version = 16;
    out->has_command = 0;
    out->lock_flag = lf;
    out->property = prop & 0xFF;
    out->weight_kg = sp_stored_weight(w.kg);
    out->weight = w;
    return SP_OK;
}

int sp_parse_gatt_v17_weight(const uint8_t *frame, int len, int command,
                              sp_gatt_t *out) {
    gatt_zero(out);
    if (len < 12) { out->error_code = SP_ERR_FORMAT; return SP_ERR_FORMAT; }

    int prop = sp_u8(frame[11]);
    sp_weight_t w;
    sp_weight_kilograms(sp_u8(frame[5]), sp_u8(frame[6]), prop, 0, 0, &w);

    out->version = 17;
    out->has_command = 1;
    out->command = command;
    out->lock_flag = command;
    out->property = prop & 0xFF;
    out->weight_kg = sp_stored_weight(w.kg);
    out->weight = w;
    return SP_OK;
}

int sp_parse_gatt_assembled(const uint8_t *body, int len, int command,
                             sp_gatt_t *out) {
    gatt_zero(out);
    if (len < 12) { out->error_code = SP_ERR_FORMAT; return SP_ERR_FORMAT; }

    int prop = sp_u8(body[4]);
    sp_weight_t w;
    sp_weight_kilograms(sp_u8(body[10]), sp_u8(body[11]), prop, 0, 0, &w);
    double weight_kg = sp_stored_weight(w.kg);

    double muscle_val = sp_recalculate_muscle(
        sp_set_muscle(sp_slice_int(body, len, 16, 2)), weight_kg);
    if (muscle_val < 0.0) {
        out->error_code = SP_ERR_FORMAT;
        return SP_ERR_FORMAT;
    }

    double visceral, ori;
    sp_set_visceral(sp_slice_int(body, len, 20, 2), &visceral, &ori);

    int64_t epoch = 0;
    int has_date = 0;
    if (command == 19) {
        epoch = (int64_t)sp_slice_int(body, len, 0, 4);
        has_date = 1;
    }

    out->version = 17;
    out->has_command = 1;
    out->command = command;
    out->lock_flag = 1;
    out->property = prop & 0xFF;
    out->weight_kg = weight_kg;
    out->history = (command == 19) ? 1 : 0;
    out->comp.axunge  = sp_set_axunge(sp_slice_int(body, len, 12, 2));
    out->comp.water   = sp_set_water(sp_slice_int(body, len, 14, 2));
    out->comp.muscle  = muscle_val;
    out->comp.bmr     = sp_set_bmr(sp_slice_int(body, len, 18, 2));
    out->comp.visceral = visceral;
    out->comp.ori     = ori;
    out->comp.bone    = sp_set_bone(sp_slice_int(body, len, 22, 1));
    out->role_id = sp_slice_int(body, len, 6, 4);
    out->has_weighing_date = has_date;
    out->weighing_date_epoch = epoch;
    out->weight = w;
    return SP_OK;
}

int sp_parse_gatt_roles(const uint8_t *body, int len, sp_confirmation_t *out) {
    memset(out, 0, sizeof(sp_confirmation_t));
    if (len < 3) { out->error_code = SP_ERR_FORMAT; return SP_ERR_FORMAT; }

    int prop = sp_u8(body[2]);
    sp_weight_t w;
    sp_weight_kilograms(sp_u8(body[0]), sp_u8(body[1]), prop, 0, 0, &w);

    out->version = 17;
    out->command = 20;
    out->property = prop & 0xFF;
    out->weight_kg = sp_stored_weight(w.kg);
    out->weight = w;

    int index = 3;
    int count = 0;
    while (index < len && count < SP_MAX_ROLES) {
        out->roles[count] = sp_slice_int((const uint8_t *)body, len, index, 4);
        count++;
        index += 4;
    }
    out->role_count = count;
    return SP_OK;
}
