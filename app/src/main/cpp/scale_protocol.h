#ifndef SCALE_PROTOCOL_H
#define SCALE_PROTOCOL_H

#include <stdint.h>
#include <stddef.h>

/* ── Error codes ── */
#define SP_OK           0
#define SP_ERR_EMPTY    1
#define SP_ERR_UNKNOWN  2
#define SP_ERR_SHORT    3
#define SP_ERR_ENCRYPTED 4
#define SP_ERR_UNSUPPORTED 5
#define SP_ERR_INCOMPLETE  6
#define SP_ERR_FORMAT   7

/* ── Unit codes ── */
#define SP_UNIT_KG  0
#define SP_UNIT_JIN 1
#define SP_UNIT_LB  2
#define SP_UNIT_ST  3

/* ── Scale-weight text mode ── */
#define SP_SW_NULL  0
#define SP_SW_TEXT  1
#define SP_SW_EMPTY 2

/* ── Scale headers ── */
#define SP_HEADER_CA 0xCA
#define SP_HEADER_C0 0xC0
#define SP_HEADER_FF 0xFF

/* ── Utility functions ── */
int sp_u8(uint8_t b);
int sp_java_byte(int value);
int sp_bytes_to_int(const uint8_t *data, int len);
int sp_bytes_to_int_safe(const uint8_t *data, int len);
int sp_slice_int(const uint8_t *frame, int frame_len, int start, int size);
void sp_split_pack(int raw, int *total, int *number);
double sp_f32(double value);
double sp_round_half_up(double value, int places, int as_f32);
double sp_stored_weight(double kg);

/* ── Weight result ── */
typedef struct {
    double kg;
    double scaled;
    int    places;
    int    unit_code;
    int    raw;
    int    high;
    int    low;
} sp_weight_t;

int sp_weight_kilograms(int high, int low, int prop,
                        int cloud, int cloud_v4,
                        sp_weight_t *out);

/* ── Composition functions ── */
double sp_set_axunge(int raw);
double sp_set_water(int raw);
double sp_set_muscle(int raw);
double sp_set_bmr(int raw);
void   sp_set_visceral(int raw, double *shown, double *ori);
double sp_set_bone(int raw);
double sp_recalculate_muscle(double muscle, double weight_kg);

typedef struct {
    double axunge;
    double water;
    double muscle;
    double bmr;
    double visceral;
    double ori;
    double bone;
} sp_composition_t;

void sp_composition(const uint8_t *frame, int frame_len, sp_composition_t *out);

/* ── Broadcast result ── */
typedef struct {
    int    error_code;
    int    header;
    int    version;
    float  weight_kg;
    int    has_impedance;
    float  impedance_ohm;
    int    product_id;
    int    device_type;
    int    cmd_id;
    int    locked;
    int    seq;
    int    property;
    int    scale_weight_mode;
    sp_weight_t weight;
} sp_broadcast_t;

int sp_parse_broadcast(const uint8_t *frame, int len, sp_broadcast_t *out);

/* ── GATT result ── */
typedef struct {
    int    error_code;
    int    version;
    int    has_command;
    int    command;
    int    lock_flag;
    int    property;
    double weight_kg;
    int    history;
    sp_composition_t comp;
    int    role_id;
    int    has_weighing_date;
    int64_t weighing_date_epoch;
    sp_weight_t weight;
} sp_gatt_t;

int sp_parse_gatt_v16(const uint8_t *frame, int len, sp_gatt_t *out);
int sp_parse_gatt_v17_weight(const uint8_t *frame, int len, int command, sp_gatt_t *out);
int sp_parse_gatt_assembled(const uint8_t *body, int len, int command, sp_gatt_t *out);

/* ── Role confirmation result ── */
#define SP_MAX_ROLES 64

typedef struct {
    int    error_code;
    int    version;
    int    command;
    int    property;
    double weight_kg;
    sp_weight_t weight;
    int    role_count;
    int    roles[SP_MAX_ROLES];
} sp_confirmation_t;

int sp_parse_gatt_roles(const uint8_t *body, int len, sp_confirmation_t *out);

/* ── Advertisement ── */
int sp_find_scale_payload(const uint8_t *record, int record_len,
                          int *payload_offset, int *payload_len);
int sp_check_manufacturer_payload(int company_id,
                                  const uint8_t *remainder, int rem_len,
                                  int *is_scale);

#endif
