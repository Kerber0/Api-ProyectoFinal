-- Base de datos PostgreSQL completa de la API Dorna.
-- Contiene la estructura y los catálogos necesarios. No contiene empresas, usuarios ni credenciales.
-- Ejecutar una sola vez sobre una base nueva y vacía con psql y ON_ERROR_STOP.

BEGIN;

--
-- PostgreSQL database dump
--


-- Dumped from database version 18.6
-- Dumped by pg_dump version 18.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: SCHEMA "public"; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA "public" IS 'standard public schema';


--
-- Name: dorna_es_ci; Type: COLLATION; Schema: public; Owner: -
--

CREATE COLLATION "public"."dorna_es_ci" (provider = icu, deterministic = false, locale = 'es-u-ks-level1');


--
-- Name: dorna_actualizar_fecha_modificacion(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."dorna_actualizar_fecha_modificacion"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
 IF NEW IS DISTINCT FROM OLD AND NEW.fecha_modificacion IS NOT DISTINCT FROM OLD.fecha_modificacion THEN
  NEW.fecha_modificacion := timezone('UTC', statement_timestamp());
 END IF;
 RETURN NEW;
END $$;


--
-- Name: dorna_auditoria_evento(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."dorna_auditoria_evento"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    PERFORM dorna_consolidar_auditoria(TG_TABLE_NAME,to_jsonb(NEW),false);
    RETURN NEW;
END $$;


--
-- Name: dorna_auditoria_inmutable(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."dorna_auditoria_inmutable"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN RAISE EXCEPTION 'El historial de auditoria es inmutable'; END $$;


--
-- Name: dorna_consolidar_auditoria("text", "jsonb", boolean); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."dorna_consolidar_auditoria"("tabla" "text", "evento" "jsonb", "historico" boolean) RETURNS "void"
    LANGUAGE "plpgsql"
    AS $$
#variable_conflict use_variable
DECLARE
    modulo TEXT; autor INTEGER; empresa INTEGER; entidad TEXT; entidad_id INTEGER;
    accion TEXT; referencias JSONB; despues JSONB; antes JSONB;
BEGIN
    autor := (evento->>'autor')::integer;
    accion := evento->>'accion';
    CASE tabla
    WHEN 'api_visibilidad_auditoria' THEN
        modulo := 'MAESTROS'; autor := (evento->>'id_usuario')::integer;
        empresa := (evento->>'empresa_origen')::integer; accion := 'COMPARTIR';
        entidad := CASE WHEN evento->>'id_cliente' IS NOT NULL THEN 'CLIENTE' ELSE 'PROVEEDOR' END;
        entidad_id := COALESCE((evento->>'id_cliente')::integer,(evento->>'id_proveedor')::integer);
        referencias := jsonb_build_object('empresaDestino',(evento->>'empresa_destino')::integer);
        despues := jsonb_build_object('visible',true,'empresaDestino',(evento->>'empresa_destino')::integer);
    WHEN 'api_usuarios_auditoria' THEN
        modulo := 'USUARIOS'; empresa := (evento->>'empresa')::integer;
        entidad := 'USUARIO'; entidad_id := (evento->>'usuario')::integer;
        referencias := jsonb_strip_nulls(jsonb_build_object('rolId',(evento->>'rol')::integer));
        SELECT jsonb_build_object('activo',activo) INTO despues FROM Usuarios WHERE id_usuario=entidad_id;
        IF accion IN ('CONCEDER_ROL','REVOCAR_ROL') THEN
            despues := referencias || jsonb_build_object('concedido',accion='CONCEDER_ROL');
        ELSIF accion='CAMBIAR_PASSWORD' THEN
            despues := jsonb_build_object('credencialRenovada',true);
        END IF;
    WHEN 'api_laboral_auditoria' THEN
        modulo := 'LABORAL'; empresa := (evento->>'empresa')::integer;
        entidad_id := (evento->>'referencia')::integer;
        referencias := jsonb_build_object('operarioId',(evento->>'operario')::integer);
        IF accion LIKE '%ASIGNACION' THEN
            entidad := 'ASIGNACION';
            SELECT jsonb_build_object('obraId',id_obra,'desde',desde,'hasta',hasta,'cancelada',cancelada)
            INTO despues FROM API_Asignaciones_Obra WHERE id=entidad_id;
        ELSIF accion LIKE '%AUSENCIA' THEN
            entidad := 'AUSENCIA';
            -- Ni motivo ni tipo de ausencia: tampoco en almacenamiento de auditoria.
            SELECT jsonb_build_object('cancelada',cancelada) INTO despues FROM API_Ausencias WHERE id=entidad_id;
        ELSE
            entidad := 'VINCULO_USUARIO';
            despues := jsonb_build_object('vinculado',accion='VINCULAR_USUARIO');
        END IF;
    WHEN 'api_observaciones_auditoria' THEN
        modulo := 'OBSERVACIONES'; entidad := 'OBSERVACION'; entidad_id := (evento->>'observacion')::integer;
        SELECT id_empresa,jsonb_build_object('obraId',id_obra),jsonb_build_object('estado',estado)
        INTO empresa,referencias,despues FROM API_Observaciones_Obra WHERE id=entidad_id;
    WHEN 'api_facturacion_auditoria' THEN
        modulo := 'FACTURACION';
        IF evento->>'documento' IS NULL THEN
            entidad := 'SERIE'; entidad_id := (evento->>'serie')::integer;
            SELECT id_empresa,jsonb_build_object('ejercicio',ejercicio,'tipo',tipo_documento,'rectificativa',rectificativa)
            INTO empresa,despues FROM Series_Documentos WHERE id_serie=entidad_id;
        ELSE
            entidad := 'FACTURA'; entidad_id := (evento->>'documento')::integer;
            SELECT id_empresa,jsonb_build_object('estado',estado,'tipo',tipo,'numero',numero,'total',total::text,'originalId',original)
            INTO empresa,despues FROM API_Facturacion WHERE id=entidad_id;
        END IF;
        referencias := '{}'::jsonb;
    WHEN 'api_tesoreria_auditoria' THEN
        modulo := 'TESORERIA';
        IF evento->>'movimiento' IS NULL THEN
            entidad := 'PLAN_VENCIMIENTOS'; entidad_id := (evento->>'documento')::integer;
            SELECT id_empresa INTO empresa FROM API_Facturacion WHERE id=entidad_id;
            SELECT jsonb_build_object('cuotas',count(*),'importe',sum(importe)::text)
            INTO despues FROM API_Vencimientos WHERE documento=entidad_id;
        ELSE
            entidad := 'MOVIMIENTO'; entidad_id := (evento->>'movimiento')::integer;
            SELECT id_empresa,jsonb_build_object('estado',estado,'tipo',tipo,'importe',importe::text,'fecha',fecha)
            INTO empresa,despues FROM API_Tesoreria WHERE id=entidad_id;
        END IF;
        referencias := '{}'::jsonb;
    WHEN 'api_documentos_auditoria' THEN
        modulo := 'DOCUMENTOS'; entidad := 'DOCUMENTO'; entidad_id := (evento->>'documento')::integer;
        SELECT id_empresa,jsonb_strip_nulls(jsonb_build_object('obraId',obra,'presupuestoId',presupuesto,'ausenciaId',ausencia)),
               jsonb_build_object('incorporado',true)
        INTO empresa,referencias,despues FROM API_Documentos WHERE id=entidad_id;
    ELSE
        RAISE EXCEPTION 'Origen de auditoria no admitido';
    END CASE;
    -- El estado actual no prueba como era un registro historico.
    IF historico THEN despues := NULL;
    ELSE
        SELECT a.cambios->'despues' INTO antes FROM API_Auditoria a
        WHERE a.modulo=modulo AND a.entidad=entidad AND a.entidad_id=entidad_id
          AND a.empresa IS NOT DISTINCT FROM empresa AND NOT a.historico
          AND a.accion NOT IN ('CONCEDER_ROL','REVOCAR_ROL','CAMBIAR_PASSWORD','COMPARTIR')
        ORDER BY a.id DESC LIMIT 1;
        -- Estas acciones describen relaciones o credenciales, no la ficha completa.
        IF accion IN ('CONCEDER_ROL','REVOCAR_ROL','CAMBIAR_PASSWORD','COMPARTIR') THEN antes := NULL; END IF;
    END IF;
    INSERT INTO API_Auditoria(modulo,origen_id,autor,empresa,entidad,entidad_id,accion,fecha,historico,cambios)
    VALUES(modulo,(evento->>'id')::bigint,autor,empresa,entidad,entidad_id,accion,(evento->>'fecha')::timestamptz,historico,
           jsonb_build_object('referencias',referencias,'antes',antes,'despues',despues));
END $$;


--
-- Name: dorna_gestion_auditoria_evento(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."dorna_gestion_auditoria_evento"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    INSERT INTO API_Auditoria(modulo,origen_id,autor,empresa,entidad,entidad_id,accion,fecha,historico,cambios)
    VALUES ('GESTION',NEW.id,NEW.autor,NEW.empresa,NEW.entidad,NEW.entidad_id,NEW.accion,NEW.fecha,false,NEW.cambios);
    RETURN NEW;
END $$;


SET default_tablespace = '';

SET default_table_access_method = "heap";

--
-- Name: albaranes_cliente; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."albaranes_cliente" (
    "id_albaran" integer NOT NULL,
    "numero" character varying(30) NOT NULL COLLATE "public"."dorna_es_ci",
    "id_serie" integer NOT NULL,
    "fecha" "date" NOT NULL,
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "subtotal" numeric(12,2) DEFAULT 0 NOT NULL,
    "descuento" numeric(12,2) DEFAULT 0 NOT NULL,
    "impuestos" numeric(12,2) DEFAULT 0 NOT NULL,
    "total" numeric(12,2) DEFAULT 0 NOT NULL,
    "id_estado" integer DEFAULT 1 NOT NULL,
    "id_cliente" integer NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer,
    "id_factura" integer
);


--
-- Name: albaranes_cliente_id_albaran_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."albaranes_cliente" ALTER COLUMN "id_albaran" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."albaranes_cliente_id_albaran_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: albaranes_proveedor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."albaranes_proveedor" (
    "id_albaran" integer NOT NULL,
    "numero" character varying(30) NOT NULL COLLATE "public"."dorna_es_ci",
    "id_serie" integer NOT NULL,
    "fecha" "date" NOT NULL,
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "subtotal" numeric(12,2) DEFAULT 0 NOT NULL,
    "descuento" numeric(12,2) DEFAULT 0 NOT NULL,
    "impuestos" numeric(12,2) DEFAULT 0 NOT NULL,
    "total" numeric(12,2) DEFAULT 0 NOT NULL,
    "id_estado" integer DEFAULT 1 NOT NULL,
    "id_proveedor" integer NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer,
    "id_factura" integer
);


--
-- Name: albaranes_proveedor_id_albaran_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."albaranes_proveedor" ALTER COLUMN "id_albaran" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."albaranes_proveedor_id_albaran_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: almacenes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."almacenes" (
    "id_almacen" integer NOT NULL,
    "codigo_almacen" character varying(20) COLLATE "public"."dorna_es_ci",
    "descripcion" character varying(300) NOT NULL COLLATE "public"."dorna_es_ci",
    "direccion" character varying(200) COLLATE "public"."dorna_es_ci",
    "ciudad" character varying(100) COLLATE "public"."dorna_es_ci",
    "provincia" character varying(100) COLLATE "public"."dorna_es_ci",
    "codigo_postal" character varying(10) COLLATE "public"."dorna_es_ci",
    "telefono" character varying(20) COLLATE "public"."dorna_es_ci",
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: almacenes_id_almacen_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."almacenes" ALTER COLUMN "id_almacen" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."almacenes_id_almacen_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_accesos_empresa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_accesos_empresa" (
    "id_usuario" integer NOT NULL,
    "id_empresa" integer NOT NULL,
    "id_rol" integer NOT NULL
);


--
-- Name: api_albaran_cliente_empresa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_albaran_cliente_empresa" (
    "id_albaran" integer NOT NULL,
    "id_empresa" integer NOT NULL
);


--
-- Name: api_albaran_proveedor_empresa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_albaran_proveedor_empresa" (
    "id_albaran" integer NOT NULL,
    "id_empresa" integer NOT NULL
);


--
-- Name: api_almacen_empresa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_almacen_empresa" (
    "id_almacen" integer NOT NULL,
    "id_empresa" integer NOT NULL
);


--
-- Name: api_archivo_documento; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_archivo_documento" (
    "id_documento" integer NOT NULL,
    "id_empresa" integer NOT NULL,
    "clave" character(36) NOT NULL COLLATE "pg_catalog"."C"
);


--
-- Name: api_articulo_empresa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_articulo_empresa" (
    "referencia_articulo" character varying(50) NOT NULL COLLATE "public"."dorna_es_ci",
    "id_empresa" integer NOT NULL
);


--
-- Name: api_asignaciones_obra; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_asignaciones_obra" (
    "id" integer NOT NULL,
    "id_empresa" integer NOT NULL,
    "id_operario" integer NOT NULL,
    "id_obra" integer NOT NULL,
    "desde" "date" NOT NULL,
    "hasta" "date",
    "cancelada" boolean DEFAULT false NOT NULL,
    CONSTRAINT "api_asignaciones_obra_check" CHECK ((("hasta" IS NULL) OR ("hasta" >= "desde")))
);


--
-- Name: api_asignaciones_obra_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_asignaciones_obra" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_asignaciones_obra_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_auditoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_auditoria" (
    "id" bigint NOT NULL,
    "modulo" character varying(20) NOT NULL,
    "origen_id" bigint NOT NULL,
    "autor" integer NOT NULL,
    "empresa" integer,
    "entidad" character varying(30) NOT NULL,
    "entidad_id" integer NOT NULL,
    "accion" character varying(40) NOT NULL,
    "fecha" timestamp with time zone NOT NULL,
    "historico" boolean NOT NULL,
    "cambios" "jsonb" NOT NULL,
    CONSTRAINT "api_auditoria_cambios_check" CHECK (("jsonb_typeof"("cambios") = 'object'::"text"))
);


--
-- Name: api_auditoria_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_auditoria" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_auditoria_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_ausencias; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_ausencias" (
    "id" integer NOT NULL,
    "id_empresa" integer NOT NULL,
    "id_operario" integer NOT NULL,
    "desde" "date" NOT NULL,
    "hasta" "date" NOT NULL,
    "tipo" character varying(20) NOT NULL,
    "cancelada" boolean DEFAULT false NOT NULL,
    "justificada" boolean,
    "observaciones" character varying(1000),
    CONSTRAINT "api_ausencias_check" CHECK (("hasta" >= "desde")),
    CONSTRAINT "ck_api_ausencias_tipo" CHECK ((("tipo")::"text" = ANY ((ARRAY['VACACIONES'::character varying, 'PERMISO'::character varying, 'BAJA'::character varying, 'FALTA'::character varying, 'OTRA'::character varying])::"text"[])))
);


--
-- Name: api_ausencias_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_ausencias" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_ausencias_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_cliente_empresa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_cliente_empresa" (
    "id_cliente" integer NOT NULL,
    "id_empresa" integer NOT NULL
);


--
-- Name: api_documentos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_documentos" (
    "id" integer NOT NULL,
    "id_empresa" integer NOT NULL,
    "obra" integer,
    "presupuesto" integer,
    "ausencia" integer,
    "nombre" character varying(200) NOT NULL,
    "mime" character varying(40) NOT NULL,
    "tamano" integer NOT NULL,
    "sha256" character varying(64) NOT NULL,
    "clave" character varying(36) NOT NULL,
    "proveedor" character varying(30) NOT NULL,
    "autor" integer NOT NULL,
    "fecha" timestamp with time zone NOT NULL,
    CONSTRAINT "api_documentos_check" CHECK (("num_nonnulls"("obra", "presupuesto", "ausencia") = 1)),
    CONSTRAINT "api_documentos_mime_check" CHECK ((("mime")::"text" = ANY ((ARRAY['application/pdf'::character varying, 'image/png'::character varying, 'image/jpeg'::character varying])::"text"[]))),
    CONSTRAINT "api_documentos_sha256_check" CHECK ((("sha256")::"text" ~ '^[a-f0-9]{64}$'::"text")),
    CONSTRAINT "api_documentos_tamano_check" CHECK ((("tamano" >= 1) AND ("tamano" <= 5242880)))
);


--
-- Name: api_documentos_auditoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_documentos_auditoria" (
    "id" bigint NOT NULL,
    "documento" integer NOT NULL,
    "autor" integer NOT NULL,
    "accion" character varying(10) NOT NULL,
    "fecha" timestamp with time zone NOT NULL,
    CONSTRAINT "api_documentos_auditoria_accion_check" CHECK ((("accion")::"text" = 'CREAR'::"text"))
);


--
-- Name: api_documentos_auditoria_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_documentos_auditoria" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_documentos_auditoria_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_documentos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_documentos" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_documentos_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_empresas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_empresas" (
    "id_empresa" integer NOT NULL,
    "nombre" character varying(150) NOT NULL COLLATE "public"."dorna_es_ci",
    "activo" boolean DEFAULT true NOT NULL,
    "id_entidad" integer
);


--
-- Name: api_evento_empresa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_evento_empresa" (
    "id_evento" integer NOT NULL,
    "id_empresa" integer NOT NULL
);


--
-- Name: api_factura_cliente_empresa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_factura_cliente_empresa" (
    "id_factura" integer NOT NULL,
    "id_empresa" integer NOT NULL
);


--
-- Name: api_factura_proveedor_empresa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_factura_proveedor_empresa" (
    "id_factura" integer NOT NULL,
    "id_empresa" integer NOT NULL
);


--
-- Name: api_facturacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_facturacion" (
    "id" integer NOT NULL,
    "id_empresa" integer NOT NULL,
    "tipo" character varying(10) NOT NULL,
    "id_serie" integer NOT NULL,
    "cliente" integer,
    "proveedor" integer,
    "obra" integer,
    "fecha" "date" NOT NULL,
    "vencimiento" "date",
    "referencia_externa" character varying(100),
    "estado" character varying(12) NOT NULL,
    "original" integer,
    "motivo" character varying(500),
    "factura_cliente" integer,
    "factura_proveedor" integer,
    "numero" character varying(30),
    "subtotal" numeric(12,2) NOT NULL,
    "descuento" numeric(12,2) NOT NULL,
    "impuestos" numeric(12,2) NOT NULL,
    "total" numeric(12,2) NOT NULL,
    "empresa_nombre" character varying(250) NOT NULL,
    "empresa_nif" character varying(20) NOT NULL,
    "tercero_nombre" character varying(250) NOT NULL,
    "tercero_nif" character varying(20) NOT NULL,
    "autor" integer NOT NULL,
    "creado" timestamp with time zone NOT NULL,
    "emitido" timestamp with time zone,
    CONSTRAINT "api_facturacion_check" CHECK ((((("tipo")::"text" = 'CLIENTE'::"text") AND ("cliente" IS NOT NULL) AND ("proveedor" IS NULL) AND ("factura_proveedor" IS NULL)) OR ((("tipo")::"text" = 'PROVEEDOR'::"text") AND ("proveedor" IS NOT NULL) AND ("cliente" IS NULL) AND ("obra" IS NULL) AND ("factura_cliente" IS NULL) AND ("referencia_externa" IS NOT NULL) AND ("btrim"(("referencia_externa")::"text") <> ''::"text")))),
    CONSTRAINT "api_facturacion_check1" CHECK ((("vencimiento" IS NULL) OR ("vencimiento" >= "fecha"))),
    CONSTRAINT "api_facturacion_check2" CHECK ((((("estado")::"text" = ANY ((ARRAY['BORRADOR'::character varying, 'ANULADA'::character varying])::"text"[])) AND ("factura_cliente" IS NULL) AND ("factura_proveedor" IS NULL) AND ("numero" IS NULL) AND ("emitido" IS NULL)) OR ((("estado")::"text" = ANY ((ARRAY['EMITIDA'::character varying, 'RECTIFICADA'::character varying])::"text"[])) AND (COALESCE("factura_cliente", "factura_proveedor") IS NOT NULL) AND ("numero" IS NOT NULL) AND ("emitido" IS NOT NULL)))),
    CONSTRAINT "api_facturacion_check3" CHECK (("total" = (("subtotal" - "descuento") + "impuestos"))),
    CONSTRAINT "api_facturacion_check4" CHECK (((("original" IS NULL) AND ("subtotal" >= (0)::numeric) AND ("descuento" >= (0)::numeric) AND ("impuestos" >= (0)::numeric) AND ("total" >= (0)::numeric)) OR (("original" IS NOT NULL) AND ("original" <> "id") AND ("subtotal" <= (0)::numeric) AND ("descuento" <= (0)::numeric) AND ("impuestos" <= (0)::numeric) AND ("total" <= (0)::numeric) AND ("motivo" IS NOT NULL)))),
    CONSTRAINT "api_facturacion_check5" CHECK (((("estado")::"text" <> 'ANULADA'::"text") OR ("motivo" IS NOT NULL))),
    CONSTRAINT "api_facturacion_estado_check" CHECK ((("estado")::"text" = ANY ((ARRAY['BORRADOR'::character varying, 'EMITIDA'::character varying, 'ANULADA'::character varying, 'RECTIFICADA'::character varying])::"text"[]))),
    CONSTRAINT "api_facturacion_tipo_check" CHECK ((("tipo")::"text" = ANY ((ARRAY['CLIENTE'::character varying, 'PROVEEDOR'::character varying])::"text"[])))
);


--
-- Name: api_facturacion_auditoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_facturacion_auditoria" (
    "id" bigint NOT NULL,
    "documento" integer,
    "serie" integer,
    "autor" integer NOT NULL,
    "accion" character varying(16) NOT NULL,
    "fecha" timestamp with time zone NOT NULL,
    "motivo" character varying(500),
    CONSTRAINT "api_facturacion_auditoria_accion_check" CHECK ((("accion")::"text" = ANY ((ARRAY['CREAR_SERIE'::character varying, 'CREAR'::character varying, 'EMITIR'::character varying, 'ANULAR'::character varying, 'RECTIFICAR'::character varying])::"text"[]))),
    CONSTRAINT "api_facturacion_auditoria_check" CHECK (((("documento" IS NULL) AND ("serie" IS NOT NULL) AND (("accion")::"text" = 'CREAR_SERIE'::"text")) OR (("documento" IS NOT NULL) AND ("serie" IS NULL) AND (("accion")::"text" <> 'CREAR_SERIE'::"text"))))
);


--
-- Name: api_facturacion_auditoria_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_facturacion_auditoria" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_facturacion_auditoria_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_facturacion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_facturacion" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_facturacion_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_facturacion_lineas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_facturacion_lineas" (
    "id" integer NOT NULL,
    "documento" integer NOT NULL,
    "posicion" integer NOT NULL,
    "descripcion" character varying(300) NOT NULL,
    "cantidad" numeric(10,2) NOT NULL,
    "precio" numeric(10,2) NOT NULL,
    "descuento" numeric(5,2) NOT NULL,
    "iva" numeric(5,2) NOT NULL,
    "bruto" numeric(12,2) NOT NULL,
    "rebaja" numeric(12,2) NOT NULL,
    "base" numeric(12,2) NOT NULL,
    "cuota" numeric(12,2) NOT NULL,
    "total" numeric(12,2) NOT NULL,
    CONSTRAINT "api_facturacion_lineas_cantidad_check" CHECK (("cantidad" <> (0)::numeric)),
    CONSTRAINT "api_facturacion_lineas_check" CHECK (("base" = ("bruto" - "rebaja"))),
    CONSTRAINT "api_facturacion_lineas_check1" CHECK (("total" = ("base" + "cuota"))),
    CONSTRAINT "api_facturacion_lineas_descuento_check" CHECK ((("descuento" >= (0)::numeric) AND ("descuento" <= (100)::numeric))),
    CONSTRAINT "api_facturacion_lineas_iva_check" CHECK ((("iva" >= (0)::numeric) AND ("iva" <= (100)::numeric))),
    CONSTRAINT "api_facturacion_lineas_posicion_check" CHECK (("posicion" > 0)),
    CONSTRAINT "api_facturacion_lineas_precio_check" CHECK (("precio" >= (0)::numeric))
);


--
-- Name: api_facturacion_lineas_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_facturacion_lineas" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_facturacion_lineas_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_gestion_auditoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_gestion_auditoria" (
    "id" bigint NOT NULL,
    "autor" integer NOT NULL,
    "empresa" integer NOT NULL,
    "entidad" character varying(30) NOT NULL,
    "entidad_id" integer NOT NULL,
    "accion" character varying(40) NOT NULL,
    "fecha" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "cambios" "jsonb" NOT NULL,
    CONSTRAINT "api_gestion_auditoria_cambios_check" CHECK (("jsonb_typeof"("cambios") = 'object'::"text")),
    CONSTRAINT "ck_dorna_gestion_auditoria_entidad" CHECK ((("entidad")::"text" = ANY ((ARRAY['CLIENTE'::character varying, 'PROVEEDOR'::character varying, 'OPERARIO'::character varying, 'OBRA'::character varying, 'PRESUPUESTO'::character varying, 'SERIE'::character varying, 'CONTACTO'::character varying, 'DIRECCION'::character varying, 'INCIDENCIA'::character varying, 'TAREA'::character varying, 'EVENTO'::character varying])::"text"[])))
);


--
-- Name: api_gestion_auditoria_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_gestion_auditoria" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_gestion_auditoria_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_incidencia_empresa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_incidencia_empresa" (
    "id_incidencia" integer NOT NULL,
    "id_empresa" integer NOT NULL
);


--
-- Name: api_laboral_auditoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_laboral_auditoria" (
    "id" bigint NOT NULL,
    "autor" integer NOT NULL,
    "empresa" integer NOT NULL,
    "operario" integer NOT NULL,
    "accion" character varying(30) NOT NULL,
    "referencia" integer NOT NULL,
    "fecha" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "api_laboral_auditoria_accion_check" CHECK ((("accion")::"text" = ANY ((ARRAY['VINCULAR_USUARIO'::character varying, 'DESVINCULAR_USUARIO'::character varying, 'CREAR_ASIGNACION'::character varying, 'CANCELAR_ASIGNACION'::character varying, 'CREAR_AUSENCIA'::character varying, 'CANCELAR_AUSENCIA'::character varying])::"text"[])))
);


--
-- Name: api_laboral_auditoria_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_laboral_auditoria" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_laboral_auditoria_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_obra_empresa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_obra_empresa" (
    "id_obra" integer NOT NULL,
    "id_empresa" integer NOT NULL
);


--
-- Name: api_observaciones_auditoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_observaciones_auditoria" (
    "id" bigint NOT NULL,
    "observacion" integer NOT NULL,
    "autor" integer NOT NULL,
    "accion" character varying(10) NOT NULL,
    "fecha" timestamp with time zone NOT NULL,
    CONSTRAINT "api_observaciones_auditoria_accion_check" CHECK ((("accion")::"text" = ANY ((ARRAY['CREAR'::character varying, 'RESOLVER'::character varying])::"text"[])))
);


--
-- Name: api_observaciones_auditoria_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_observaciones_auditoria" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_observaciones_auditoria_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_observaciones_obra; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_observaciones_obra" (
    "id" integer NOT NULL,
    "id_empresa" integer NOT NULL,
    "id_obra" integer NOT NULL,
    "autor" integer NOT NULL,
    "fecha_creacion" timestamp with time zone NOT NULL,
    "texto" "text" NOT NULL,
    "estado" character varying(10) DEFAULT 'ABIERTA'::character varying NOT NULL,
    "resuelto_por" integer,
    "fecha_resolucion" timestamp with time zone,
    "resolucion" "text",
    CONSTRAINT "api_observaciones_obra_check" CHECK ((((("estado")::"text" = 'ABIERTA'::"text") AND ("resuelto_por" IS NULL) AND ("fecha_resolucion" IS NULL) AND ("resolucion" IS NULL)) OR ((("estado")::"text" = 'RESUELTA'::"text") AND ("resuelto_por" IS NOT NULL) AND ("fecha_resolucion" IS NOT NULL) AND ("fecha_resolucion" >= "fecha_creacion") AND ("resolucion" IS NOT NULL) AND (("char_length"("resolucion") >= 1) AND ("char_length"("resolucion") <= 2000)) AND ("btrim"("resolucion") <> ''::"text")))),
    CONSTRAINT "api_observaciones_obra_estado_check" CHECK ((("estado")::"text" = ANY ((ARRAY['ABIERTA'::character varying, 'RESUELTA'::character varying])::"text"[]))),
    CONSTRAINT "api_observaciones_obra_texto_check" CHECK (((("char_length"("texto") >= 1) AND ("char_length"("texto") <= 2000)) AND ("btrim"("texto") <> ''::"text")))
);


--
-- Name: api_observaciones_obra_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_observaciones_obra" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_observaciones_obra_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_operario_empresa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_operario_empresa" (
    "id_operario" integer NOT NULL,
    "id_empresa" integer NOT NULL
);


--
-- Name: api_pedido_proveedor_empresa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_pedido_proveedor_empresa" (
    "id_pedido" integer NOT NULL,
    "id_empresa" integer NOT NULL
);


--
-- Name: api_presupuesto_empresa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_presupuesto_empresa" (
    "id_presupuesto" integer NOT NULL,
    "id_empresa" integer NOT NULL
);


--
-- Name: api_proveedor_empresa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_proveedor_empresa" (
    "id_proveedor" integer NOT NULL,
    "id_empresa" integer NOT NULL
);


--
-- Name: api_roles_globales; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_roles_globales" (
    "id_rol" integer NOT NULL
);


--
-- Name: api_sesiones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_sesiones" (
    "id_sesion" character(36) NOT NULL COLLATE "pg_catalog"."C",
    "id_usuario" integer NOT NULL,
    "refresh_hash" character(64) NOT NULL COLLATE "pg_catalog"."C",
    "version" integer DEFAULT 0 NOT NULL,
    "expira" timestamp(6) with time zone NOT NULL,
    "revocada" boolean DEFAULT false NOT NULL
);


--
-- Name: api_tarea_empresa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_tarea_empresa" (
    "id_tarea" integer NOT NULL,
    "id_empresa" integer NOT NULL
);


--
-- Name: api_tesoreria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_tesoreria" (
    "id" integer NOT NULL,
    "id_empresa" integer NOT NULL,
    "tipo" character varying(10) NOT NULL,
    "tercero" integer NOT NULL,
    "clave" character varying(36) NOT NULL,
    "huella" character varying(64) NOT NULL,
    "fecha" "date" NOT NULL,
    "importe" numeric(12,2) NOT NULL,
    "metodo" character varying(20) NOT NULL,
    "estado" character varying(12) NOT NULL,
    "referencia" character varying(100),
    "conciliado" timestamp with time zone,
    "motivo" character varying(500),
    "fecha_reversion" "date",
    "autor" integer NOT NULL,
    "creado" timestamp with time zone NOT NULL,
    CONSTRAINT "api_tesoreria_check" CHECK (((("referencia" IS NULL) AND ("conciliado" IS NULL)) OR (("referencia" IS NOT NULL) AND ("conciliado" IS NOT NULL)))),
    CONSTRAINT "api_tesoreria_check1" CHECK (((("estado")::"text" <> 'CONCILIADO'::"text") OR ("referencia" IS NOT NULL))),
    CONSTRAINT "api_tesoreria_check2" CHECK ((((("estado")::"text" = 'REVERTIDO'::"text") AND ("motivo" IS NOT NULL) AND ("fecha_reversion" >= "fecha")) OR ((("estado")::"text" <> 'REVERTIDO'::"text") AND ("motivo" IS NULL) AND ("fecha_reversion" IS NULL)))),
    CONSTRAINT "api_tesoreria_estado_check" CHECK ((("estado")::"text" = ANY ((ARRAY['REGISTRADO'::character varying, 'CONCILIADO'::character varying, 'REVERTIDO'::character varying])::"text"[]))),
    CONSTRAINT "api_tesoreria_importe_check" CHECK (("importe" <> (0)::numeric)),
    CONSTRAINT "api_tesoreria_metodo_check" CHECK ((("metodo")::"text" = ANY ((ARRAY['EFECTIVO'::character varying, 'TRANSFERENCIA'::character varying, 'TARJETA'::character varying, 'BIZUM'::character varying, 'CHEQUE'::character varying])::"text"[]))),
    CONSTRAINT "api_tesoreria_tipo_check" CHECK ((("tipo")::"text" = ANY ((ARRAY['CLIENTE'::character varying, 'PROVEEDOR'::character varying])::"text"[])))
);


--
-- Name: api_tesoreria_aplicaciones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_tesoreria_aplicaciones" (
    "id" integer NOT NULL,
    "movimiento" integer NOT NULL,
    "vencimiento" integer NOT NULL,
    "importe" numeric(12,2) NOT NULL,
    "cobro" integer,
    "pago" integer,
    "cobro_reversion" integer,
    "pago_reversion" integer,
    CONSTRAINT "api_tesoreria_aplicaciones_check" CHECK (((("cobro" IS NOT NULL) AND ("pago" IS NULL) AND ("pago_reversion" IS NULL)) OR (("pago" IS NOT NULL) AND ("cobro" IS NULL) AND ("cobro_reversion" IS NULL)))),
    CONSTRAINT "api_tesoreria_aplicaciones_importe_check" CHECK (("importe" <> (0)::numeric))
);


--
-- Name: api_tesoreria_aplicaciones_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_tesoreria_aplicaciones" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_tesoreria_aplicaciones_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_tesoreria_auditoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_tesoreria_auditoria" (
    "id" bigint NOT NULL,
    "documento" integer,
    "movimiento" integer,
    "autor" integer NOT NULL,
    "accion" character varying(16) NOT NULL,
    "fecha" timestamp with time zone NOT NULL,
    "detalle" character varying(500),
    CONSTRAINT "api_tesoreria_auditoria_accion_check" CHECK ((("accion")::"text" = ANY ((ARRAY['PLANIFICAR'::character varying, 'REGISTRAR'::character varying, 'CONCILIAR'::character varying, 'REVERTIR'::character varying])::"text"[]))),
    CONSTRAINT "api_tesoreria_auditoria_check" CHECK (((("documento" IS NOT NULL) AND ("movimiento" IS NULL) AND (("accion")::"text" = 'PLANIFICAR'::"text")) OR (("documento" IS NULL) AND ("movimiento" IS NOT NULL) AND (("accion")::"text" <> 'PLANIFICAR'::"text"))))
);


--
-- Name: api_tesoreria_auditoria_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_tesoreria_auditoria" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_tesoreria_auditoria_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_tesoreria_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_tesoreria" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_tesoreria_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_traspaso_stock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_traspaso_stock" (
    "id_movimiento" integer NOT NULL,
    "id_almacen_destino" integer NOT NULL
);


--
-- Name: api_usuario_operario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_usuario_operario" (
    "id_usuario" integer NOT NULL,
    "id_operario" integer NOT NULL
);


--
-- Name: api_usuarios_auditoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_usuarios_auditoria" (
    "id" bigint NOT NULL,
    "autor" integer NOT NULL,
    "usuario" integer NOT NULL,
    "accion" character varying(40) NOT NULL,
    "empresa" integer,
    "rol" integer,
    "fecha" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "api_usuarios_auditoria_accion_check" CHECK ((("accion")::"text" = ANY ((ARRAY['CREAR'::character varying, 'ACTUALIZAR'::character varying, 'CAMBIAR_PASSWORD'::character varying, 'CONCEDER_ROL'::character varying, 'REVOCAR_ROL'::character varying])::"text"[])))
);


--
-- Name: api_usuarios_auditoria_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_usuarios_auditoria" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_usuarios_auditoria_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_vencimientos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_vencimientos" (
    "id" integer NOT NULL,
    "documento" integer NOT NULL,
    "posicion" integer NOT NULL,
    "fecha" "date" NOT NULL,
    "importe" numeric(12,2) NOT NULL,
    CONSTRAINT "api_vencimientos_importe_check" CHECK (("importe" <> (0)::numeric)),
    CONSTRAINT "api_vencimientos_posicion_check" CHECK ((("posicion" >= 1) AND ("posicion" <= 50)))
);


--
-- Name: api_vencimientos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_vencimientos" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_vencimientos_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: api_visibilidad_auditoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."api_visibilidad_auditoria" (
    "id" bigint NOT NULL,
    "id_usuario" integer NOT NULL,
    "empresa_origen" integer NOT NULL,
    "empresa_destino" integer NOT NULL,
    "id_cliente" integer,
    "id_proveedor" integer,
    "fecha" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "api_visibilidad_auditoria_check" CHECK ((((("id_cliente" IS NOT NULL))::integer + (("id_proveedor" IS NOT NULL))::integer) = 1)),
    CONSTRAINT "api_visibilidad_auditoria_check1" CHECK (("empresa_origen" <> "empresa_destino"))
);


--
-- Name: api_visibilidad_auditoria_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."api_visibilidad_auditoria" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."api_visibilidad_auditoria_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: articulos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."articulos" (
    "referencia" character varying(50) NOT NULL COLLATE "public"."dorna_es_ci",
    "codigo_barras" character varying(50) COLLATE "public"."dorna_es_ci",
    "descripcion" character varying(300) NOT NULL COLLATE "public"."dorna_es_ci",
    "marca" character varying(100) COLLATE "public"."dorna_es_ci",
    "modelo" character varying(100) COLLATE "public"."dorna_es_ci",
    "precio_coste" numeric(10,2) DEFAULT 0 NOT NULL,
    "precio_venta" numeric(10,2) DEFAULT 0 NOT NULL,
    "stock_minimo" numeric(10,2) DEFAULT 0 NOT NULL,
    "stock_maximo" numeric(10,2) DEFAULT 0 NOT NULL,
    "peso" numeric(10,2),
    "ubicacion" character varying(100) COLLATE "public"."dorna_es_ci",
    "activo" boolean DEFAULT true NOT NULL,
    "id_familia" integer,
    "id_iva" integer,
    "id_unidad" integer,
    "id_proveedor" integer,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: categorias_operarios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."categorias_operarios" (
    "id_categoria" integer NOT NULL,
    "descripcion" character varying(300) NOT NULL COLLATE "public"."dorna_es_ci",
    "coste_hora_base" numeric(10,2) NOT NULL,
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: categorias_operarios_id_categoria_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."categorias_operarios" ALTER COLUMN "id_categoria" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."categorias_operarios_id_categoria_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: clientes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."clientes" (
    "id_cliente" integer NOT NULL,
    "id_entidad" integer NOT NULL,
    "codigo_cliente" character varying(20) NOT NULL COLLATE "public"."dorna_es_ci",
    "descuento" numeric(5,2) DEFAULT 0 NOT NULL,
    "limite_credito" numeric(12,2),
    "id_zona" integer,
    "id_forma_pago" integer,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer,
    "version_gestion" bigint DEFAULT 0 NOT NULL,
    "iban" character varying(34)
);


--
-- Name: clientes_id_cliente_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."clientes" ALTER COLUMN "id_cliente" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."clientes_id_cliente_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: cobros; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."cobros" (
    "id_cobro" integer NOT NULL,
    "id_factura" integer NOT NULL,
    "fecha" "date" NOT NULL,
    "importe" numeric(12,2) NOT NULL,
    "metodo_pago" character varying(20) COLLATE "public"."dorna_es_ci",
    "numero_operacion" character varying(100) COLLATE "public"."dorna_es_ci",
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer,
    CONSTRAINT "chk_cobro_metodo_pago" CHECK ((("metodo_pago" IS NULL) OR (("metodo_pago")::"text" = ANY ((ARRAY['EFECTIVO'::character varying, 'TRANSFERENCIA'::character varying, 'TARJETA'::character varying, 'BIZUM'::character varying, 'CHEQUE'::character varying])::"text"[]))))
);


--
-- Name: cobros_id_cobro_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."cobros" ALTER COLUMN "id_cobro" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."cobros_id_cobro_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: contactos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."contactos" (
    "id_contacto" integer NOT NULL,
    "id_entidad" integer NOT NULL,
    "nombre" character varying(100) NOT NULL COLLATE "public"."dorna_es_ci",
    "apellidos" character varying(150) COLLATE "public"."dorna_es_ci",
    "cargo" character varying(100) COLLATE "public"."dorna_es_ci",
    "departamento" character varying(100) COLLATE "public"."dorna_es_ci",
    "telefono" character varying(20) COLLATE "public"."dorna_es_ci",
    "movil" character varying(20) COLLATE "public"."dorna_es_ci",
    "email" character varying(150) COLLATE "public"."dorna_es_ci",
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "principal" boolean DEFAULT false NOT NULL,
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: contactos_id_contacto_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."contactos" ALTER COLUMN "id_contacto" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."contactos_id_contacto_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: direcciones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."direcciones" (
    "id_direccion" integer NOT NULL,
    "id_entidad" integer NOT NULL,
    "tipo_direccion" character varying(50) NOT NULL COLLATE "public"."dorna_es_ci",
    "direccion" character varying(200) NOT NULL COLLATE "public"."dorna_es_ci",
    "codigo_postal" character varying(10) COLLATE "public"."dorna_es_ci",
    "poblacion" character varying(100) COLLATE "public"."dorna_es_ci",
    "provincia" character varying(100) COLLATE "public"."dorna_es_ci",
    "pais" character varying(100) DEFAULT 'Espana'::character varying NOT NULL COLLATE "public"."dorna_es_ci",
    "telefono" character varying(20) COLLATE "public"."dorna_es_ci",
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "principal" boolean DEFAULT false NOT NULL,
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: direcciones_id_direccion_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."direcciones" ALTER COLUMN "id_direccion" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."direcciones_id_direccion_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: documentos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."documentos" (
    "id_documento" integer NOT NULL,
    "id_obra" integer NOT NULL,
    "nombre" character varying(200) NOT NULL COLLATE "public"."dorna_es_ci",
    "ruta" character varying(500) NOT NULL COLLATE "public"."dorna_es_ci",
    "tipo" character varying(50) COLLATE "public"."dorna_es_ci",
    "fecha_subida" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: documentos_generales; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."documentos_generales" (
    "id_documento" integer NOT NULL,
    "nombre" character varying(200) NOT NULL COLLATE "public"."dorna_es_ci",
    "descripcion" "text" COLLATE "public"."dorna_es_ci",
    "ruta" character varying(500) NOT NULL COLLATE "public"."dorna_es_ci",
    "tipo" character varying(50) COLLATE "public"."dorna_es_ci",
    "fecha_subida" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "id_usuario" integer,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: documentos_generales_id_documento_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."documentos_generales" ALTER COLUMN "id_documento" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."documentos_generales_id_documento_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: documentos_id_documento_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."documentos" ALTER COLUMN "id_documento" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."documentos_id_documento_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: entidades; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."entidades" (
    "id_entidad" integer NOT NULL,
    "tipo_entidad" character varying(10) NOT NULL COLLATE "public"."dorna_es_ci",
    "nombre" character varying(100) COLLATE "public"."dorna_es_ci",
    "apellidos" character varying(150) COLLATE "public"."dorna_es_ci",
    "razon_social" character varying(150) COLLATE "public"."dorna_es_ci",
    "nombre_comercial" character varying(150) COLLATE "public"."dorna_es_ci",
    "nif_cif" character varying(20) COLLATE "public"."dorna_es_ci",
    "telefono_fijo" character varying(20) COLLATE "public"."dorna_es_ci",
    "movil" character varying(20) COLLATE "public"."dorna_es_ci",
    "email" character varying(150) COLLATE "public"."dorna_es_ci",
    "web" character varying(150) COLLATE "public"."dorna_es_ci",
    "fecha_nacimiento" "date",
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_alta" "date" DEFAULT CURRENT_DATE NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer,
    CONSTRAINT "chk_entidad_persona_empresa" CHECK ((((("tipo_entidad")::"text" = 'PERSONA'::"text") AND ("nombre" IS NOT NULL) AND ("razon_social" IS NULL)) OR ((("tipo_entidad")::"text" = 'EMPRESA'::"text") AND ("razon_social" IS NOT NULL) AND ("nombre" IS NULL) AND ("apellidos" IS NULL)))),
    CONSTRAINT "chk_entidad_tipo" CHECK ((("tipo_entidad")::"text" = ANY ((ARRAY['PERSONA'::character varying, 'EMPRESA'::character varying])::"text"[])))
);


--
-- Name: entidades_id_entidad_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."entidades" ALTER COLUMN "id_entidad" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."entidades_id_entidad_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: estados_factura; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."estados_factura" (
    "id_estado" integer NOT NULL,
    "descripcion" character varying(300) NOT NULL COLLATE "public"."dorna_es_ci",
    "color" character varying(20) COLLATE "public"."dorna_es_ci",
    "orden_visualizacion" integer DEFAULT 0 NOT NULL,
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: estados_factura_id_estado_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."estados_factura" ALTER COLUMN "id_estado" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."estados_factura_id_estado_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: estados_incidencia; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."estados_incidencia" (
    "id_estado" integer NOT NULL,
    "descripcion" character varying(300) NOT NULL COLLATE "public"."dorna_es_ci",
    "color" character varying(20) COLLATE "public"."dorna_es_ci",
    "orden_visualizacion" integer DEFAULT 0 NOT NULL,
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: estados_incidencia_id_estado_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."estados_incidencia" ALTER COLUMN "id_estado" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."estados_incidencia_id_estado_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: estados_obra; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."estados_obra" (
    "id_estado" integer NOT NULL,
    "descripcion" character varying(300) NOT NULL COLLATE "public"."dorna_es_ci",
    "color" character varying(20) COLLATE "public"."dorna_es_ci",
    "orden_visualizacion" integer DEFAULT 0 NOT NULL,
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: estados_obra_id_estado_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."estados_obra" ALTER COLUMN "id_estado" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."estados_obra_id_estado_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: estados_tarea; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."estados_tarea" (
    "id_estado" integer NOT NULL,
    "descripcion" character varying(300) NOT NULL COLLATE "public"."dorna_es_ci",
    "color" character varying(20) COLLATE "public"."dorna_es_ci",
    "orden_visualizacion" integer DEFAULT 0 NOT NULL,
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: estados_tarea_id_estado_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."estados_tarea" ALTER COLUMN "id_estado" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."estados_tarea_id_estado_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: eventos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."eventos" (
    "id_evento" integer NOT NULL,
    "titulo" character varying(150) NOT NULL COLLATE "public"."dorna_es_ci",
    "descripcion" "text" COLLATE "public"."dorna_es_ci",
    "fecha_inicio" timestamp(6) without time zone NOT NULL,
    "fecha_fin" timestamp(6) without time zone,
    "color" character varying(20) COLLATE "public"."dorna_es_ci",
    "tipo_evento" character varying(30) DEFAULT 'OBRA'::character varying NOT NULL COLLATE "public"."dorna_es_ci",
    "id_obra" integer,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer,
    CONSTRAINT "chk_evento_tipo" CHECK ((("tipo_evento")::"text" = ANY ((ARRAY['OBRA'::character varying, 'REUNION'::character varying, 'VACACIONES'::character varying, 'AVISO'::character varying, 'PERSONAL'::character varying])::"text"[])))
);


--
-- Name: eventos_id_evento_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."eventos" ALTER COLUMN "id_evento" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."eventos_id_evento_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: facturas_cliente; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."facturas_cliente" (
    "id_factura" integer NOT NULL,
    "numero" character varying(30) NOT NULL COLLATE "public"."dorna_es_ci",
    "id_serie" integer NOT NULL,
    "fecha" "date" NOT NULL,
    "fecha_vencimiento" "date",
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "subtotal" numeric(12,2) DEFAULT 0 NOT NULL,
    "descuento" numeric(12,2) DEFAULT 0 NOT NULL,
    "impuestos" numeric(12,2) DEFAULT 0 NOT NULL,
    "total" numeric(12,2) DEFAULT 0 NOT NULL,
    "id_estado" integer DEFAULT 1 NOT NULL,
    "id_cliente" integer NOT NULL,
    "id_obra" integer,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: facturas_cliente_id_factura_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."facturas_cliente" ALTER COLUMN "id_factura" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."facturas_cliente_id_factura_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: facturas_proveedor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."facturas_proveedor" (
    "id_factura" integer NOT NULL,
    "numero" character varying(30) NOT NULL COLLATE "public"."dorna_es_ci",
    "id_serie" integer NOT NULL,
    "fecha" "date" NOT NULL,
    "fecha_vencimiento" "date",
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "subtotal" numeric(12,2) DEFAULT 0 NOT NULL,
    "descuento" numeric(12,2) DEFAULT 0 NOT NULL,
    "impuestos" numeric(12,2) DEFAULT 0 NOT NULL,
    "total" numeric(12,2) DEFAULT 0 NOT NULL,
    "id_estado" integer DEFAULT 1 NOT NULL,
    "id_proveedor" integer NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: facturas_proveedor_id_factura_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."facturas_proveedor" ALTER COLUMN "id_factura" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."facturas_proveedor_id_factura_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: familias; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."familias" (
    "id_familia" integer NOT NULL,
    "descripcion" character varying(300) NOT NULL COLLATE "public"."dorna_es_ci",
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: familias_id_familia_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."familias" ALTER COLUMN "id_familia" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."familias_id_familia_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: formas_pago; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."formas_pago" (
    "id_forma_pago" integer NOT NULL,
    "descripcion" character varying(300) NOT NULL COLLATE "public"."dorna_es_ci",
    "dias_vencimiento" integer DEFAULT 0 NOT NULL,
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: formas_pago_id_forma_pago_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."formas_pago" ALTER COLUMN "id_forma_pago" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."formas_pago_id_forma_pago_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: historial_estados_obra; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."historial_estados_obra" (
    "id_historial" integer NOT NULL,
    "id_obra" integer NOT NULL,
    "id_estado_anterior" integer,
    "id_estado_nuevo" integer NOT NULL,
    "fecha" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "id_usuario" integer,
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: historial_estados_obra_id_historial_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."historial_estados_obra" ALTER COLUMN "id_historial" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."historial_estados_obra_id_historial_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: incidencias; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."incidencias" (
    "id_incidencia" integer NOT NULL,
    "id_obra" integer NOT NULL,
    "id_operario" integer,
    "fecha" "date" NOT NULL,
    "descripcion" "text" NOT NULL COLLATE "public"."dorna_es_ci",
    "id_estado" integer DEFAULT 1 NOT NULL,
    "id_prioridad" integer DEFAULT 2 NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: incidencias_id_incidencia_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."incidencias" ALTER COLUMN "id_incidencia" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."incidencias_id_incidencia_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: lineas_albaran_cliente; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."lineas_albaran_cliente" (
    "id_linea" integer NOT NULL,
    "id_albaran" integer NOT NULL,
    "referencia_articulo" character varying(50) COLLATE "public"."dorna_es_ci",
    "descripcion" character varying(300) COLLATE "public"."dorna_es_ci",
    "cantidad" numeric(10,2) NOT NULL,
    "precio" numeric(10,2) NOT NULL,
    "descuento" numeric(5,2) DEFAULT 0 NOT NULL,
    "iva" numeric(5,2) DEFAULT 21 NOT NULL,
    "importe" numeric(12,2),
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: lineas_albaran_cliente_id_linea_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."lineas_albaran_cliente" ALTER COLUMN "id_linea" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lineas_albaran_cliente_id_linea_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: lineas_albaran_proveedor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."lineas_albaran_proveedor" (
    "id_linea" integer NOT NULL,
    "id_albaran" integer NOT NULL,
    "referencia_articulo" character varying(50) COLLATE "public"."dorna_es_ci",
    "descripcion" character varying(300) COLLATE "public"."dorna_es_ci",
    "cantidad" numeric(10,2) NOT NULL,
    "precio" numeric(10,2) NOT NULL,
    "descuento" numeric(5,2) DEFAULT 0 NOT NULL,
    "iva" numeric(5,2) DEFAULT 21 NOT NULL,
    "importe" numeric(12,2),
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: lineas_albaran_proveedor_id_linea_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."lineas_albaran_proveedor" ALTER COLUMN "id_linea" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lineas_albaran_proveedor_id_linea_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: lineas_factura_cliente; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."lineas_factura_cliente" (
    "id_linea" integer NOT NULL,
    "id_factura" integer NOT NULL,
    "referencia_articulo" character varying(50) COLLATE "public"."dorna_es_ci",
    "descripcion" character varying(300) COLLATE "public"."dorna_es_ci",
    "cantidad" numeric(10,2),
    "precio" numeric(10,2),
    "descuento" numeric(5,2),
    "iva" numeric(5,2),
    "importe" numeric(12,2),
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: lineas_factura_cliente_id_linea_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."lineas_factura_cliente" ALTER COLUMN "id_linea" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lineas_factura_cliente_id_linea_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: lineas_factura_proveedor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."lineas_factura_proveedor" (
    "id_linea" integer NOT NULL,
    "id_factura" integer NOT NULL,
    "referencia_articulo" character varying(50) COLLATE "public"."dorna_es_ci",
    "descripcion" character varying(300) COLLATE "public"."dorna_es_ci",
    "cantidad" numeric(10,2),
    "precio" numeric(10,2),
    "descuento" numeric(5,2),
    "iva" numeric(5,2),
    "importe" numeric(12,2),
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: lineas_factura_proveedor_id_linea_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."lineas_factura_proveedor" ALTER COLUMN "id_linea" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lineas_factura_proveedor_id_linea_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: lineas_pedido; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."lineas_pedido" (
    "id_linea" integer NOT NULL,
    "id_pedido" integer NOT NULL,
    "referencia_articulo" character varying(50) NOT NULL COLLATE "public"."dorna_es_ci",
    "descripcion" character varying(300) COLLATE "public"."dorna_es_ci",
    "cantidad" numeric(10,2) NOT NULL,
    "precio_coste" numeric(10,2) NOT NULL,
    "descuento" numeric(5,2) DEFAULT 0 NOT NULL,
    "iva" numeric(5,2) DEFAULT 21 NOT NULL,
    "importe" numeric(12,2) NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: lineas_pedido_id_linea_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."lineas_pedido" ALTER COLUMN "id_linea" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lineas_pedido_id_linea_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: lineas_presupuesto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."lineas_presupuesto" (
    "id_linea" integer NOT NULL,
    "id_presupuesto" integer NOT NULL,
    "referencia_articulo" character varying(50) COLLATE "public"."dorna_es_ci",
    "descripcion" character varying(300) NOT NULL COLLATE "public"."dorna_es_ci",
    "cantidad" numeric(10,2) NOT NULL,
    "precio" numeric(10,2) NOT NULL,
    "descuento" numeric(5,2) DEFAULT 0 NOT NULL,
    "iva" numeric(5,2) DEFAULT 21 NOT NULL,
    "importe" numeric(12,2) NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: lineas_presupuesto_id_linea_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."lineas_presupuesto" ALTER COLUMN "id_linea" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lineas_presupuesto_id_linea_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."log" (
    "id_log" integer NOT NULL,
    "fecha" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "id_usuario" integer,
    "accion" character varying(150) NOT NULL COLLATE "public"."dorna_es_ci",
    "tabla_afectada" character varying(100) COLLATE "public"."dorna_es_ci",
    "id_registro" integer,
    "descripcion" "text" COLLATE "public"."dorna_es_ci",
    "ip" character varying(45) COLLATE "public"."dorna_es_ci"
);


--
-- Name: log_id_log_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."log" ALTER COLUMN "id_log" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."log_id_log_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: materiales_por_obra; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."materiales_por_obra" (
    "id_material" integer NOT NULL,
    "id_obra" integer NOT NULL,
    "referencia_articulo" character varying(50) NOT NULL COLLATE "public"."dorna_es_ci",
    "id_almacen" integer,
    "fecha" "date" NOT NULL,
    "cantidad" numeric(10,2) NOT NULL,
    "coste_unitario" numeric(10,2) NOT NULL,
    "importe" numeric(12,2) NOT NULL,
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: materiales_por_obra_id_material_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."materiales_por_obra" ALTER COLUMN "id_material" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."materiales_por_obra_id_material_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: movimientos_stock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."movimientos_stock" (
    "id_movimiento" integer NOT NULL,
    "referencia_articulo" character varying(50) NOT NULL COLLATE "public"."dorna_es_ci",
    "id_almacen" integer NOT NULL,
    "fecha" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "tipo_movimiento" character varying(20) NOT NULL COLLATE "public"."dorna_es_ci",
    "origen" character varying(100) COLLATE "public"."dorna_es_ci",
    "destino" character varying(100) COLLATE "public"."dorna_es_ci",
    "documento" character varying(50) COLLATE "public"."dorna_es_ci",
    "cantidad" numeric(10,2) NOT NULL,
    "precio_unitario" numeric(10,2),
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer,
    CONSTRAINT "chk_stock_tipo_movimiento" CHECK ((("tipo_movimiento")::"text" = ANY ((ARRAY['ENTRADA'::character varying, 'SALIDA'::character varying, 'AJUSTE'::character varying, 'TRASPASO'::character varying])::"text"[])))
);


--
-- Name: movimientos_stock_id_movimiento_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."movimientos_stock" ALTER COLUMN "id_movimiento" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."movimientos_stock_id_movimiento_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: obra_contacto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."obra_contacto" (
    "id_obra_contacto" integer NOT NULL,
    "id_obra" integer NOT NULL,
    "id_contacto" integer NOT NULL,
    "rol_en_obra" character varying(80) NOT NULL COLLATE "public"."dorna_es_ci",
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: obra_contacto_id_obra_contacto_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."obra_contacto" ALTER COLUMN "id_obra_contacto" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."obra_contacto_id_obra_contacto_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: obras; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."obras" (
    "id_obra" integer NOT NULL,
    "id_serie" integer NOT NULL,
    "codigo_obra" character varying(20) NOT NULL COLLATE "public"."dorna_es_ci",
    "titulo" character varying(200) NOT NULL COLLATE "public"."dorna_es_ci",
    "descripcion" "text" COLLATE "public"."dorna_es_ci",
    "presupuesto" numeric(12,2) DEFAULT 0 NOT NULL,
    "fecha_inicio" "date",
    "fecha_fin_prevista" "date",
    "fecha_fin_real" "date",
    "id_estado" integer DEFAULT 1 NOT NULL,
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "id_cliente" integer NOT NULL,
    "id_direccion" integer,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer,
    "version_gestion" bigint DEFAULT 0 NOT NULL
);


--
-- Name: obras_id_obra_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."obras" ALTER COLUMN "id_obra" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."obras_id_obra_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: operarios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."operarios" (
    "id_operario" integer NOT NULL,
    "id_entidad" integer NOT NULL,
    "codigo_operario" character varying(20) NOT NULL COLLATE "public"."dorna_es_ci",
    "fecha_alta" "date" DEFAULT CURRENT_DATE NOT NULL,
    "fecha_baja" "date",
    "nss" character varying(30) COLLATE "public"."dorna_es_ci",
    "horas_semanales" numeric(5,2),
    "coste_hora_real" numeric(10,2),
    "id_categoria" integer,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer,
    "version_gestion" bigint DEFAULT 0 NOT NULL,
    "iban" character varying(34)
);


--
-- Name: operarios_id_operario_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."operarios" ALTER COLUMN "id_operario" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."operarios_id_operario_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: pagos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."pagos" (
    "id_pago" integer NOT NULL,
    "id_factura" integer NOT NULL,
    "fecha" "date" NOT NULL,
    "importe" numeric(12,2) NOT NULL,
    "metodo_pago" character varying(20) COLLATE "public"."dorna_es_ci",
    "numero_operacion" character varying(100) COLLATE "public"."dorna_es_ci",
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer,
    CONSTRAINT "chk_pago_metodo_pago" CHECK ((("metodo_pago" IS NULL) OR (("metodo_pago")::"text" = ANY ((ARRAY['EFECTIVO'::character varying, 'TRANSFERENCIA'::character varying, 'TARJETA'::character varying, 'BIZUM'::character varying, 'CHEQUE'::character varying])::"text"[]))))
);


--
-- Name: pagos_id_pago_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."pagos" ALTER COLUMN "id_pago" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."pagos_id_pago_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: partes_mano_obra; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."partes_mano_obra" (
    "id_parte" integer NOT NULL,
    "id_obra" integer NOT NULL,
    "id_operario" integer NOT NULL,
    "fecha" "date" NOT NULL,
    "hora_inicio" time without time zone,
    "hora_fin" time without time zone,
    "horas_trabajadas" numeric(5,2) NOT NULL,
    "coste_hora" numeric(10,2) NOT NULL,
    "coste_total" numeric(10,2) NOT NULL,
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: partes_mano_obra_id_parte_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."partes_mano_obra" ALTER COLUMN "id_parte" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."partes_mano_obra_id_parte_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: pedidos_proveedor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."pedidos_proveedor" (
    "id_pedido" integer NOT NULL,
    "numero" character varying(30) NOT NULL COLLATE "public"."dorna_es_ci",
    "id_serie" integer NOT NULL,
    "fecha" "date" NOT NULL,
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "subtotal" numeric(12,2) DEFAULT 0 NOT NULL,
    "descuento" numeric(12,2) DEFAULT 0 NOT NULL,
    "impuestos" numeric(12,2) DEFAULT 0 NOT NULL,
    "total" numeric(12,2) DEFAULT 0 NOT NULL,
    "id_estado" integer DEFAULT 1 NOT NULL,
    "id_proveedor" integer NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: pedidos_proveedor_id_pedido_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."pedidos_proveedor" ALTER COLUMN "id_pedido" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."pedidos_proveedor_id_pedido_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: permisos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."permisos" (
    "id_permiso" integer NOT NULL,
    "nombre" character varying(80) NOT NULL COLLATE "public"."dorna_es_ci",
    "descripcion" character varying(300) COLLATE "public"."dorna_es_ci",
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: permisos_id_permiso_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."permisos" ALTER COLUMN "id_permiso" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."permisos_id_permiso_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: presupuestos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."presupuestos" (
    "id_presupuesto" integer NOT NULL,
    "numero" character varying(30) NOT NULL COLLATE "public"."dorna_es_ci",
    "id_serie" integer NOT NULL,
    "fecha" "date" NOT NULL,
    "validez" "date",
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "subtotal" numeric(12,2) DEFAULT 0 NOT NULL,
    "descuento" numeric(12,2) DEFAULT 0 NOT NULL,
    "impuestos" numeric(12,2) DEFAULT 0 NOT NULL,
    "total" numeric(12,2) DEFAULT 0 NOT NULL,
    "id_estado" integer DEFAULT 1 NOT NULL,
    "id_cliente" integer NOT NULL,
    "id_obra" integer,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer,
    "version_gestion" bigint DEFAULT 0 NOT NULL,
    "gestionado" boolean DEFAULT false NOT NULL
);


--
-- Name: presupuestos_id_presupuesto_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."presupuestos" ALTER COLUMN "id_presupuesto" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."presupuestos_id_presupuesto_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: prioridades; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."prioridades" (
    "id_prioridad" integer NOT NULL,
    "descripcion" character varying(300) NOT NULL COLLATE "public"."dorna_es_ci",
    "color" character varying(20) COLLATE "public"."dorna_es_ci",
    "orden_visualizacion" integer DEFAULT 0 NOT NULL,
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: prioridades_id_prioridad_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."prioridades" ALTER COLUMN "id_prioridad" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."prioridades_id_prioridad_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: proveedores; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."proveedores" (
    "id_proveedor" integer NOT NULL,
    "id_entidad" integer NOT NULL,
    "codigo_proveedor" character varying(20) NOT NULL COLLATE "public"."dorna_es_ci",
    "tipo_proveedor" character varying(20) DEFAULT 'EMPRESA'::character varying NOT NULL COLLATE "public"."dorna_es_ci",
    "iban" character varying(34) COLLATE "public"."dorna_es_ci",
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer,
    "version_gestion" bigint DEFAULT 0 NOT NULL,
    CONSTRAINT "chk_proveedor_tipo" CHECK ((("tipo_proveedor")::"text" = ANY ((ARRAY['EMPRESA'::character varying, 'AUTONOMO'::character varying])::"text"[])))
);


--
-- Name: proveedores_id_proveedor_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."proveedores" ALTER COLUMN "id_proveedor" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."proveedores_id_proveedor_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: rol_permiso; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."rol_permiso" (
    "id_rol_permiso" integer NOT NULL,
    "id_rol" integer NOT NULL,
    "id_permiso" integer NOT NULL,
    "nivel_acceso" character varying(10) DEFAULT 'LECTURA'::character varying NOT NULL COLLATE "public"."dorna_es_ci",
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer,
    CONSTRAINT "chk_rol_permiso_nivel" CHECK ((("nivel_acceso")::"text" = ANY ((ARRAY['LECTURA'::character varying, 'ESCRITURA'::character varying, 'ADMIN'::character varying])::"text"[])))
);


--
-- Name: rol_permiso_id_rol_permiso_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."rol_permiso" ALTER COLUMN "id_rol_permiso" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."rol_permiso_id_rol_permiso_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."roles" (
    "id_rol" integer NOT NULL,
    "nombre" character varying(50) NOT NULL COLLATE "public"."dorna_es_ci",
    "descripcion" character varying(300) COLLATE "public"."dorna_es_ci",
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: roles_id_rol_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."roles" ALTER COLUMN "id_rol" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."roles_id_rol_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: series_documentos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."series_documentos" (
    "id_serie" integer NOT NULL,
    "tipo_documento" character varying(40) NOT NULL COLLATE "public"."dorna_es_ci",
    "nombre" character varying(100) NOT NULL COLLATE "public"."dorna_es_ci",
    "prefijo" character varying(20) NOT NULL COLLATE "public"."dorna_es_ci",
    "siguiente_numero" integer DEFAULT 1 NOT NULL,
    "longitud" integer DEFAULT 6 NOT NULL,
    "ejercicio" integer NOT NULL,
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer,
    "id_empresa" integer,
    "rectificativa" boolean DEFAULT false NOT NULL,
    "ultima_fecha" "date",
    CONSTRAINT "chk_serie_tipo_documento" CHECK ((("tipo_documento")::"text" = ANY ((ARRAY['OBRA'::character varying, 'PRESUPUESTO'::character varying, 'PEDIDO_PROVEEDOR'::character varying, 'ALBARAN_CLIENTE'::character varying, 'ALBARAN_PROVEEDOR'::character varying, 'FACTURA_CLIENTE'::character varying, 'FACTURA_PROVEEDOR'::character varying])::"text"[]))),
    CONSTRAINT "ck_serie_gestion" CHECK ((("id_empresa" IS NULL) OR ((("tipo_documento")::"text" = ANY ((ARRAY['FACTURA_CLIENTE'::character varying, 'FACTURA_PROVEEDOR'::character varying, 'OBRA'::character varying, 'PRESUPUESTO'::character varying])::"text"[])) AND (("ejercicio" >= 2000) AND ("ejercicio" <= 9999)) AND (("longitud" >= 1) AND ("longitud" <= 9)) AND ("siguiente_numero" > 0))))
);


--
-- Name: series_documentos_id_serie_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."series_documentos" ALTER COLUMN "id_serie" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."series_documentos_id_serie_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tareas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."tareas" (
    "id_tarea" integer NOT NULL,
    "titulo" character varying(150) NOT NULL COLLATE "public"."dorna_es_ci",
    "descripcion" "text" COLLATE "public"."dorna_es_ci",
    "id_prioridad" integer DEFAULT 2 NOT NULL,
    "id_estado" integer DEFAULT 1 NOT NULL,
    "fecha_limite" "date",
    "fecha_finalizacion" "date",
    "id_obra" integer,
    "id_operario" integer,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: tareas_id_tarea_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."tareas" ALTER COLUMN "id_tarea" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."tareas_id_tarea_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tipos_iva; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."tipos_iva" (
    "id_iva" integer NOT NULL,
    "descripcion" character varying(300) NOT NULL COLLATE "public"."dorna_es_ci",
    "porcentaje" numeric(5,2) NOT NULL,
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: tipos_iva_id_iva_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."tipos_iva" ALTER COLUMN "id_iva" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."tipos_iva_id_iva_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: unidades_medida; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."unidades_medida" (
    "id_unidad" integer NOT NULL,
    "descripcion" character varying(300) NOT NULL COLLATE "public"."dorna_es_ci",
    "abreviatura" character varying(10) NOT NULL COLLATE "public"."dorna_es_ci",
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: unidades_medida_id_unidad_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."unidades_medida" ALTER COLUMN "id_unidad" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."unidades_medida_id_unidad_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: usuario_rol; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."usuario_rol" (
    "id_usuario_rol" integer NOT NULL,
    "id_usuario" integer NOT NULL,
    "id_rol" integer NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: usuario_rol_id_usuario_rol_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."usuario_rol" ALTER COLUMN "id_usuario_rol" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."usuario_rol_id_usuario_rol_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: usuarios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."usuarios" (
    "id_usuario" integer NOT NULL,
    "id_entidad" integer,
    "nombre_usuario" character varying(50) NOT NULL COLLATE "public"."dorna_es_ci",
    "password_hash" character varying(255) NOT NULL COLLATE "pg_catalog"."C",
    "email_login" character varying(150) COLLATE "public"."dorna_es_ci",
    "ultimo_login" timestamp(6) without time zone,
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: usuarios_id_usuario_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."usuarios" ALTER COLUMN "id_usuario" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."usuarios_id_usuario_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: vehiculos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."vehiculos" (
    "id_vehiculo" integer NOT NULL,
    "matricula" character varying(15) NOT NULL COLLATE "public"."dorna_es_ci",
    "marca" character varying(100) NOT NULL COLLATE "public"."dorna_es_ci",
    "modelo" character varying(100) NOT NULL COLLATE "public"."dorna_es_ci",
    "version" character varying(100) COLLATE "public"."dorna_es_ci",
    "combustible" character varying(30) COLLATE "public"."dorna_es_ci",
    "color" character varying(30) COLLATE "public"."dorna_es_ci",
    "fecha_matriculacion" "date",
    "kilometros" integer DEFAULT 0 NOT NULL,
    "fecha_itv" "date",
    "fecha_seguro" "date",
    "numero_poliza" character varying(100) COLLATE "public"."dorna_es_ci",
    "compania_seguro" character varying(100) COLLATE "public"."dorna_es_ci",
    "observaciones" "text" COLLATE "public"."dorna_es_ci",
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: vehiculos_id_vehiculo_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."vehiculos" ALTER COLUMN "id_vehiculo" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."vehiculos_id_vehiculo_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: vw_beneficio_obra; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."vw_beneficio_obra" AS
 SELECT "ob"."id_obra",
    "ob"."titulo",
    COALESCE("fc"."ingresos", (0)::numeric) AS "ingresos",
    (COALESCE("pm"."coste", (0)::numeric) + COALESCE("mp"."importe", (0)::numeric)) AS "gastos",
    ((COALESCE("fc"."ingresos", (0)::numeric) - COALESCE("pm"."coste", (0)::numeric)) - COALESCE("mp"."importe", (0)::numeric)) AS "beneficio"
   FROM ((("public"."obras" "ob"
     LEFT JOIN ( SELECT "facturas_cliente"."id_obra",
            "sum"("facturas_cliente"."total") AS "ingresos"
           FROM "public"."facturas_cliente"
          GROUP BY "facturas_cliente"."id_obra") "fc" ON (("fc"."id_obra" = "ob"."id_obra")))
     LEFT JOIN ( SELECT "partes_mano_obra"."id_obra",
            "sum"("partes_mano_obra"."coste_total") AS "coste"
           FROM "public"."partes_mano_obra"
          GROUP BY "partes_mano_obra"."id_obra") "pm" ON (("pm"."id_obra" = "ob"."id_obra")))
     LEFT JOIN ( SELECT "materiales_por_obra"."id_obra",
            "sum"("materiales_por_obra"."importe") AS "importe"
           FROM "public"."materiales_por_obra"
          GROUP BY "materiales_por_obra"."id_obra") "mp" ON (("mp"."id_obra" = "ob"."id_obra")));


--
-- Name: vw_cobros_mes; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."vw_cobros_mes" AS
 SELECT (EXTRACT("year" FROM "fecha"))::integer AS "anio",
    (EXTRACT("month" FROM "fecha"))::integer AS "mes",
    "sum"("importe") AS "total"
   FROM "public"."cobros"
  GROUP BY ((EXTRACT("year" FROM "fecha"))::integer), ((EXTRACT("month" FROM "fecha"))::integer);


--
-- Name: vw_facturacion_mensual; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."vw_facturacion_mensual" AS
 SELECT (EXTRACT("year" FROM "fecha"))::integer AS "anio",
    (EXTRACT("month" FROM "fecha"))::integer AS "mes",
    "count"(*) AS "total_facturas",
    "sum"("total") AS "facturacion"
   FROM "public"."facturas_cliente"
  GROUP BY ((EXTRACT("year" FROM "fecha"))::integer), ((EXTRACT("month" FROM "fecha"))::integer);


--
-- Name: vw_facturas_pendientes; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."vw_facturas_pendientes" AS
 SELECT "fc"."numero",
    "fc"."fecha",
    "fc"."fecha_vencimiento",
    "fc"."total",
    "ef"."descripcion" AS "estado",
    "fc"."id_cliente"
   FROM ("public"."facturas_cliente" "fc"
     JOIN "public"."estados_factura" "ef" ON (("fc"."id_estado" = "ef"."id_estado")))
  WHERE (("ef"."descripcion")::"text" <> 'PAGADA'::"text");


--
-- Name: vw_obras_activas; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."vw_obras_activas" AS
 SELECT "ob"."id_obra",
    "ob"."id_serie",
    "ob"."codigo_obra",
    "ob"."titulo",
    "ob"."descripcion",
    "ob"."presupuesto",
    "ob"."fecha_inicio",
    "ob"."fecha_fin_prevista",
    "ob"."fecha_fin_real",
    "ob"."id_estado",
    "ob"."observaciones",
    "ob"."id_cliente",
    "ob"."id_direccion",
    "ob"."fecha_creacion",
    "ob"."fecha_modificacion",
    "ob"."usuario_creacion",
    "ob"."usuario_modificacion",
    "eo"."descripcion" AS "estado"
   FROM ("public"."obras" "ob"
     JOIN "public"."estados_obra" "eo" ON (("ob"."id_estado" = "eo"."id_estado")))
  WHERE (("eo"."descripcion")::"text" = 'EN_CURSO'::"text");


--
-- Name: vw_operarios_horas; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."vw_operarios_horas" AS
 SELECT "o"."id_operario",
    TRIM(BOTH FROM "concat_ws"(' '::"text", "e"."nombre", "e"."apellidos")) AS "nombre_completo",
    "sum"("p"."horas_trabajadas") AS "horas",
    "sum"("p"."coste_total") AS "coste"
   FROM (("public"."operarios" "o"
     JOIN "public"."entidades" "e" ON (("o"."id_entidad" = "e"."id_entidad")))
     LEFT JOIN "public"."partes_mano_obra" "p" ON (("o"."id_operario" = "p"."id_operario")))
  GROUP BY "o"."id_operario", "e"."nombre", "e"."apellidos";


--
-- Name: vw_pagos_mes; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."vw_pagos_mes" AS
 SELECT (EXTRACT("year" FROM "fecha"))::integer AS "anio",
    (EXTRACT("month" FROM "fecha"))::integer AS "mes",
    "sum"("importe") AS "total"
   FROM "public"."pagos"
  GROUP BY ((EXTRACT("year" FROM "fecha"))::integer), ((EXTRACT("month" FROM "fecha"))::integer);


--
-- Name: vw_stock_actual; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."vw_stock_actual" AS
 SELECT "a"."referencia",
    "a"."descripcion",
    COALESCE("sum"(
        CASE "ms"."tipo_movimiento"
            WHEN 'ENTRADA'::"text" THEN "ms"."cantidad"
            WHEN 'SALIDA'::"text" THEN (- "ms"."cantidad")
            WHEN 'AJUSTE'::"text" THEN "ms"."cantidad"
            ELSE (0)::numeric
        END), (0)::numeric) AS "stock_actual",
    "a"."stock_minimo",
    "a"."stock_maximo",
    "f"."descripcion" AS "familia"
   FROM (("public"."articulos" "a"
     LEFT JOIN "public"."movimientos_stock" "ms" ON ((("a"."referencia")::"text" = ("ms"."referencia_articulo")::"text")))
     LEFT JOIN "public"."familias" "f" ON (("a"."id_familia" = "f"."id_familia")))
  GROUP BY "a"."referencia", "a"."descripcion", "a"."stock_minimo", "a"."stock_maximo", "f"."descripcion";


--
-- Name: vw_top_clientes; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."vw_top_clientes" AS
 SELECT "c"."id_cliente",
    COALESCE("e"."nombre_comercial", "e"."razon_social", (TRIM(BOTH FROM "concat_ws"(' '::"text", "e"."nombre", "e"."apellidos")))::character varying) AS "nombre_comercial",
    "sum"("f"."total") AS "total_facturado"
   FROM (("public"."clientes" "c"
     JOIN "public"."entidades" "e" ON (("c"."id_entidad" = "e"."id_entidad")))
     JOIN "public"."facturas_cliente" "f" ON (("c"."id_cliente" = "f"."id_cliente")))
  GROUP BY "c"."id_cliente", "e"."nombre_comercial", "e"."razon_social", "e"."nombre", "e"."apellidos"
  ORDER BY ("sum"("f"."total")) DESC;


--
-- Name: zonas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."zonas" (
    "id_zona" integer NOT NULL,
    "descripcion" character varying(300) NOT NULL COLLATE "public"."dorna_es_ci",
    "activo" boolean DEFAULT true NOT NULL,
    "fecha_creacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "fecha_modificacion" timestamp(6) without time zone DEFAULT "timezone"('UTC'::"text", "statement_timestamp"()) NOT NULL,
    "usuario_creacion" integer,
    "usuario_modificacion" integer
);


--
-- Name: zonas_id_zona_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE "public"."zonas" ALTER COLUMN "id_zona" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."zonas_id_zona_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: albaranes_cliente albaranes_cliente_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."albaranes_cliente"
    ADD CONSTRAINT "albaranes_cliente_pkey" PRIMARY KEY ("id_albaran");


--
-- Name: albaranes_proveedor albaranes_proveedor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."albaranes_proveedor"
    ADD CONSTRAINT "albaranes_proveedor_pkey" PRIMARY KEY ("id_albaran");


--
-- Name: almacenes almacenes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."almacenes"
    ADD CONSTRAINT "almacenes_pkey" PRIMARY KEY ("id_almacen");


--
-- Name: api_accesos_empresa api_accesos_empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_accesos_empresa"
    ADD CONSTRAINT "api_accesos_empresa_pkey" PRIMARY KEY ("id_usuario", "id_empresa", "id_rol");


--
-- Name: api_albaran_cliente_empresa api_albaran_cliente_empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_albaran_cliente_empresa"
    ADD CONSTRAINT "api_albaran_cliente_empresa_pkey" PRIMARY KEY ("id_albaran");


--
-- Name: api_albaran_proveedor_empresa api_albaran_proveedor_empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_albaran_proveedor_empresa"
    ADD CONSTRAINT "api_albaran_proveedor_empresa_pkey" PRIMARY KEY ("id_albaran");


--
-- Name: api_almacen_empresa api_almacen_empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_almacen_empresa"
    ADD CONSTRAINT "api_almacen_empresa_pkey" PRIMARY KEY ("id_almacen");


--
-- Name: api_archivo_documento api_archivo_documento_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_archivo_documento"
    ADD CONSTRAINT "api_archivo_documento_pkey" PRIMARY KEY ("id_documento");


--
-- Name: api_articulo_empresa api_articulo_empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_articulo_empresa"
    ADD CONSTRAINT "api_articulo_empresa_pkey" PRIMARY KEY ("referencia_articulo", "id_empresa");


--
-- Name: api_asignaciones_obra api_asignaciones_obra_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_asignaciones_obra"
    ADD CONSTRAINT "api_asignaciones_obra_pkey" PRIMARY KEY ("id");


--
-- Name: api_auditoria api_auditoria_modulo_origen_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_auditoria"
    ADD CONSTRAINT "api_auditoria_modulo_origen_id_key" UNIQUE ("modulo", "origen_id");


--
-- Name: api_auditoria api_auditoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_auditoria"
    ADD CONSTRAINT "api_auditoria_pkey" PRIMARY KEY ("id");


--
-- Name: api_ausencias api_ausencias_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_ausencias"
    ADD CONSTRAINT "api_ausencias_pkey" PRIMARY KEY ("id");


--
-- Name: api_cliente_empresa api_cliente_empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_cliente_empresa"
    ADD CONSTRAINT "api_cliente_empresa_pkey" PRIMARY KEY ("id_cliente", "id_empresa");


--
-- Name: api_documentos_auditoria api_documentos_auditoria_documento_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_documentos_auditoria"
    ADD CONSTRAINT "api_documentos_auditoria_documento_key" UNIQUE ("documento");


--
-- Name: api_documentos_auditoria api_documentos_auditoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_documentos_auditoria"
    ADD CONSTRAINT "api_documentos_auditoria_pkey" PRIMARY KEY ("id");


--
-- Name: api_documentos api_documentos_clave_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_documentos"
    ADD CONSTRAINT "api_documentos_clave_key" UNIQUE ("clave");


--
-- Name: api_documentos api_documentos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_documentos"
    ADD CONSTRAINT "api_documentos_pkey" PRIMARY KEY ("id");


--
-- Name: api_empresas api_empresas_id_entidad_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_empresas"
    ADD CONSTRAINT "api_empresas_id_entidad_key" UNIQUE ("id_entidad");


--
-- Name: api_empresas api_empresas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_empresas"
    ADD CONSTRAINT "api_empresas_pkey" PRIMARY KEY ("id_empresa");


--
-- Name: api_evento_empresa api_evento_empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_evento_empresa"
    ADD CONSTRAINT "api_evento_empresa_pkey" PRIMARY KEY ("id_evento");


--
-- Name: api_factura_cliente_empresa api_factura_cliente_empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_factura_cliente_empresa"
    ADD CONSTRAINT "api_factura_cliente_empresa_pkey" PRIMARY KEY ("id_factura");


--
-- Name: api_factura_proveedor_empresa api_factura_proveedor_empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_factura_proveedor_empresa"
    ADD CONSTRAINT "api_factura_proveedor_empresa_pkey" PRIMARY KEY ("id_factura");


--
-- Name: api_facturacion_auditoria api_facturacion_auditoria_documento_accion_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion_auditoria"
    ADD CONSTRAINT "api_facturacion_auditoria_documento_accion_key" UNIQUE ("documento", "accion");


--
-- Name: api_facturacion_auditoria api_facturacion_auditoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion_auditoria"
    ADD CONSTRAINT "api_facturacion_auditoria_pkey" PRIMARY KEY ("id");


--
-- Name: api_facturacion_auditoria api_facturacion_auditoria_serie_accion_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion_auditoria"
    ADD CONSTRAINT "api_facturacion_auditoria_serie_accion_key" UNIQUE ("serie", "accion");


--
-- Name: api_facturacion api_facturacion_factura_cliente_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion"
    ADD CONSTRAINT "api_facturacion_factura_cliente_key" UNIQUE ("factura_cliente");


--
-- Name: api_facturacion api_facturacion_factura_proveedor_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion"
    ADD CONSTRAINT "api_facturacion_factura_proveedor_key" UNIQUE ("factura_proveedor");


--
-- Name: api_facturacion api_facturacion_id_id_empresa_tipo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion"
    ADD CONSTRAINT "api_facturacion_id_id_empresa_tipo_key" UNIQUE ("id", "id_empresa", "tipo");


--
-- Name: api_facturacion_lineas api_facturacion_lineas_documento_posicion_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion_lineas"
    ADD CONSTRAINT "api_facturacion_lineas_documento_posicion_key" UNIQUE ("documento", "posicion");


--
-- Name: api_facturacion_lineas api_facturacion_lineas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion_lineas"
    ADD CONSTRAINT "api_facturacion_lineas_pkey" PRIMARY KEY ("id");


--
-- Name: api_facturacion api_facturacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion"
    ADD CONSTRAINT "api_facturacion_pkey" PRIMARY KEY ("id");


--
-- Name: api_gestion_auditoria api_gestion_auditoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_gestion_auditoria"
    ADD CONSTRAINT "api_gestion_auditoria_pkey" PRIMARY KEY ("id");


--
-- Name: api_incidencia_empresa api_incidencia_empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_incidencia_empresa"
    ADD CONSTRAINT "api_incidencia_empresa_pkey" PRIMARY KEY ("id_incidencia");


--
-- Name: api_laboral_auditoria api_laboral_auditoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_laboral_auditoria"
    ADD CONSTRAINT "api_laboral_auditoria_pkey" PRIMARY KEY ("id");


--
-- Name: api_obra_empresa api_obra_empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_obra_empresa"
    ADD CONSTRAINT "api_obra_empresa_pkey" PRIMARY KEY ("id_obra");


--
-- Name: api_observaciones_auditoria api_observaciones_auditoria_observacion_accion_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_observaciones_auditoria"
    ADD CONSTRAINT "api_observaciones_auditoria_observacion_accion_key" UNIQUE ("observacion", "accion");


--
-- Name: api_observaciones_auditoria api_observaciones_auditoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_observaciones_auditoria"
    ADD CONSTRAINT "api_observaciones_auditoria_pkey" PRIMARY KEY ("id");


--
-- Name: api_observaciones_obra api_observaciones_obra_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_observaciones_obra"
    ADD CONSTRAINT "api_observaciones_obra_pkey" PRIMARY KEY ("id");


--
-- Name: api_operario_empresa api_operario_empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_operario_empresa"
    ADD CONSTRAINT "api_operario_empresa_pkey" PRIMARY KEY ("id_operario");


--
-- Name: api_pedido_proveedor_empresa api_pedido_proveedor_empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_pedido_proveedor_empresa"
    ADD CONSTRAINT "api_pedido_proveedor_empresa_pkey" PRIMARY KEY ("id_pedido");


--
-- Name: api_presupuesto_empresa api_presupuesto_empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_presupuesto_empresa"
    ADD CONSTRAINT "api_presupuesto_empresa_pkey" PRIMARY KEY ("id_presupuesto");


--
-- Name: api_proveedor_empresa api_proveedor_empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_proveedor_empresa"
    ADD CONSTRAINT "api_proveedor_empresa_pkey" PRIMARY KEY ("id_proveedor", "id_empresa");


--
-- Name: api_roles_globales api_roles_globales_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_roles_globales"
    ADD CONSTRAINT "api_roles_globales_pkey" PRIMARY KEY ("id_rol");


--
-- Name: api_sesiones api_sesiones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_sesiones"
    ADD CONSTRAINT "api_sesiones_pkey" PRIMARY KEY ("id_sesion");


--
-- Name: api_tarea_empresa api_tarea_empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tarea_empresa"
    ADD CONSTRAINT "api_tarea_empresa_pkey" PRIMARY KEY ("id_tarea");


--
-- Name: api_tesoreria_aplicaciones api_tesoreria_aplicaciones_cobro_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_aplicaciones"
    ADD CONSTRAINT "api_tesoreria_aplicaciones_cobro_key" UNIQUE ("cobro");


--
-- Name: api_tesoreria_aplicaciones api_tesoreria_aplicaciones_cobro_reversion_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_aplicaciones"
    ADD CONSTRAINT "api_tesoreria_aplicaciones_cobro_reversion_key" UNIQUE ("cobro_reversion");


--
-- Name: api_tesoreria_aplicaciones api_tesoreria_aplicaciones_movimiento_vencimiento_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_aplicaciones"
    ADD CONSTRAINT "api_tesoreria_aplicaciones_movimiento_vencimiento_key" UNIQUE ("movimiento", "vencimiento");


--
-- Name: api_tesoreria_aplicaciones api_tesoreria_aplicaciones_pago_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_aplicaciones"
    ADD CONSTRAINT "api_tesoreria_aplicaciones_pago_key" UNIQUE ("pago");


--
-- Name: api_tesoreria_aplicaciones api_tesoreria_aplicaciones_pago_reversion_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_aplicaciones"
    ADD CONSTRAINT "api_tesoreria_aplicaciones_pago_reversion_key" UNIQUE ("pago_reversion");


--
-- Name: api_tesoreria_aplicaciones api_tesoreria_aplicaciones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_aplicaciones"
    ADD CONSTRAINT "api_tesoreria_aplicaciones_pkey" PRIMARY KEY ("id");


--
-- Name: api_tesoreria_auditoria api_tesoreria_auditoria_documento_accion_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_auditoria"
    ADD CONSTRAINT "api_tesoreria_auditoria_documento_accion_key" UNIQUE ("documento", "accion");


--
-- Name: api_tesoreria_auditoria api_tesoreria_auditoria_movimiento_accion_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_auditoria"
    ADD CONSTRAINT "api_tesoreria_auditoria_movimiento_accion_key" UNIQUE ("movimiento", "accion");


--
-- Name: api_tesoreria_auditoria api_tesoreria_auditoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_auditoria"
    ADD CONSTRAINT "api_tesoreria_auditoria_pkey" PRIMARY KEY ("id");


--
-- Name: api_tesoreria api_tesoreria_id_empresa_tipo_clave_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria"
    ADD CONSTRAINT "api_tesoreria_id_empresa_tipo_clave_key" UNIQUE ("id_empresa", "tipo", "clave");


--
-- Name: api_tesoreria api_tesoreria_id_empresa_tipo_referencia_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria"
    ADD CONSTRAINT "api_tesoreria_id_empresa_tipo_referencia_key" UNIQUE ("id_empresa", "tipo", "referencia");


--
-- Name: api_tesoreria api_tesoreria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria"
    ADD CONSTRAINT "api_tesoreria_pkey" PRIMARY KEY ("id");


--
-- Name: api_traspaso_stock api_traspaso_stock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_traspaso_stock"
    ADD CONSTRAINT "api_traspaso_stock_pkey" PRIMARY KEY ("id_movimiento");


--
-- Name: api_usuario_operario api_usuario_operario_id_operario_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_usuario_operario"
    ADD CONSTRAINT "api_usuario_operario_id_operario_key" UNIQUE ("id_operario");


--
-- Name: api_usuario_operario api_usuario_operario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_usuario_operario"
    ADD CONSTRAINT "api_usuario_operario_pkey" PRIMARY KEY ("id_usuario");


--
-- Name: api_usuarios_auditoria api_usuarios_auditoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_usuarios_auditoria"
    ADD CONSTRAINT "api_usuarios_auditoria_pkey" PRIMARY KEY ("id");


--
-- Name: api_vencimientos api_vencimientos_documento_posicion_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_vencimientos"
    ADD CONSTRAINT "api_vencimientos_documento_posicion_key" UNIQUE ("documento", "posicion");


--
-- Name: api_vencimientos api_vencimientos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_vencimientos"
    ADD CONSTRAINT "api_vencimientos_pkey" PRIMARY KEY ("id");


--
-- Name: api_visibilidad_auditoria api_visibilidad_auditoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_visibilidad_auditoria"
    ADD CONSTRAINT "api_visibilidad_auditoria_pkey" PRIMARY KEY ("id");


--
-- Name: articulos articulos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."articulos"
    ADD CONSTRAINT "articulos_pkey" PRIMARY KEY ("referencia");


--
-- Name: categorias_operarios categorias_operarios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."categorias_operarios"
    ADD CONSTRAINT "categorias_operarios_pkey" PRIMARY KEY ("id_categoria");


--
-- Name: clientes clientes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."clientes"
    ADD CONSTRAINT "clientes_pkey" PRIMARY KEY ("id_cliente");


--
-- Name: cobros cobros_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."cobros"
    ADD CONSTRAINT "cobros_pkey" PRIMARY KEY ("id_cobro");


--
-- Name: contactos contactos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."contactos"
    ADD CONSTRAINT "contactos_pkey" PRIMARY KEY ("id_contacto");


--
-- Name: direcciones direcciones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."direcciones"
    ADD CONSTRAINT "direcciones_pkey" PRIMARY KEY ("id_direccion");


--
-- Name: documentos_generales documentos_generales_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."documentos_generales"
    ADD CONSTRAINT "documentos_generales_pkey" PRIMARY KEY ("id_documento");


--
-- Name: documentos documentos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."documentos"
    ADD CONSTRAINT "documentos_pkey" PRIMARY KEY ("id_documento");


--
-- Name: entidades entidades_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."entidades"
    ADD CONSTRAINT "entidades_pkey" PRIMARY KEY ("id_entidad");


--
-- Name: estados_factura estados_factura_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."estados_factura"
    ADD CONSTRAINT "estados_factura_pkey" PRIMARY KEY ("id_estado");


--
-- Name: estados_incidencia estados_incidencia_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."estados_incidencia"
    ADD CONSTRAINT "estados_incidencia_pkey" PRIMARY KEY ("id_estado");


--
-- Name: estados_obra estados_obra_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."estados_obra"
    ADD CONSTRAINT "estados_obra_pkey" PRIMARY KEY ("id_estado");


--
-- Name: estados_tarea estados_tarea_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."estados_tarea"
    ADD CONSTRAINT "estados_tarea_pkey" PRIMARY KEY ("id_estado");


--
-- Name: eventos eventos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."eventos"
    ADD CONSTRAINT "eventos_pkey" PRIMARY KEY ("id_evento");


--
-- Name: facturas_cliente facturas_cliente_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."facturas_cliente"
    ADD CONSTRAINT "facturas_cliente_pkey" PRIMARY KEY ("id_factura");


--
-- Name: facturas_proveedor facturas_proveedor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."facturas_proveedor"
    ADD CONSTRAINT "facturas_proveedor_pkey" PRIMARY KEY ("id_factura");


--
-- Name: familias familias_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."familias"
    ADD CONSTRAINT "familias_pkey" PRIMARY KEY ("id_familia");


--
-- Name: formas_pago formas_pago_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."formas_pago"
    ADD CONSTRAINT "formas_pago_pkey" PRIMARY KEY ("id_forma_pago");


--
-- Name: historial_estados_obra historial_estados_obra_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."historial_estados_obra"
    ADD CONSTRAINT "historial_estados_obra_pkey" PRIMARY KEY ("id_historial");


--
-- Name: incidencias incidencias_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."incidencias"
    ADD CONSTRAINT "incidencias_pkey" PRIMARY KEY ("id_incidencia");


--
-- Name: lineas_albaran_cliente lineas_albaran_cliente_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_albaran_cliente"
    ADD CONSTRAINT "lineas_albaran_cliente_pkey" PRIMARY KEY ("id_linea");


--
-- Name: lineas_albaran_proveedor lineas_albaran_proveedor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_albaran_proveedor"
    ADD CONSTRAINT "lineas_albaran_proveedor_pkey" PRIMARY KEY ("id_linea");


--
-- Name: lineas_factura_cliente lineas_factura_cliente_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_factura_cliente"
    ADD CONSTRAINT "lineas_factura_cliente_pkey" PRIMARY KEY ("id_linea");


--
-- Name: lineas_factura_proveedor lineas_factura_proveedor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_factura_proveedor"
    ADD CONSTRAINT "lineas_factura_proveedor_pkey" PRIMARY KEY ("id_linea");


--
-- Name: lineas_pedido lineas_pedido_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_pedido"
    ADD CONSTRAINT "lineas_pedido_pkey" PRIMARY KEY ("id_linea");


--
-- Name: lineas_presupuesto lineas_presupuesto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_presupuesto"
    ADD CONSTRAINT "lineas_presupuesto_pkey" PRIMARY KEY ("id_linea");


--
-- Name: log log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."log"
    ADD CONSTRAINT "log_pkey" PRIMARY KEY ("id_log");


--
-- Name: materiales_por_obra materiales_por_obra_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."materiales_por_obra"
    ADD CONSTRAINT "materiales_por_obra_pkey" PRIMARY KEY ("id_material");


--
-- Name: movimientos_stock movimientos_stock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."movimientos_stock"
    ADD CONSTRAINT "movimientos_stock_pkey" PRIMARY KEY ("id_movimiento");


--
-- Name: obra_contacto obra_contacto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."obra_contacto"
    ADD CONSTRAINT "obra_contacto_pkey" PRIMARY KEY ("id_obra_contacto");


--
-- Name: obras obras_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."obras"
    ADD CONSTRAINT "obras_pkey" PRIMARY KEY ("id_obra");


--
-- Name: operarios operarios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."operarios"
    ADD CONSTRAINT "operarios_pkey" PRIMARY KEY ("id_operario");


--
-- Name: pagos pagos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."pagos"
    ADD CONSTRAINT "pagos_pkey" PRIMARY KEY ("id_pago");


--
-- Name: partes_mano_obra partes_mano_obra_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."partes_mano_obra"
    ADD CONSTRAINT "partes_mano_obra_pkey" PRIMARY KEY ("id_parte");


--
-- Name: pedidos_proveedor pedidos_proveedor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."pedidos_proveedor"
    ADD CONSTRAINT "pedidos_proveedor_pkey" PRIMARY KEY ("id_pedido");


--
-- Name: permisos permisos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."permisos"
    ADD CONSTRAINT "permisos_pkey" PRIMARY KEY ("id_permiso");


--
-- Name: presupuestos presupuestos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."presupuestos"
    ADD CONSTRAINT "presupuestos_pkey" PRIMARY KEY ("id_presupuesto");


--
-- Name: prioridades prioridades_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."prioridades"
    ADD CONSTRAINT "prioridades_pkey" PRIMARY KEY ("id_prioridad");


--
-- Name: proveedores proveedores_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."proveedores"
    ADD CONSTRAINT "proveedores_pkey" PRIMARY KEY ("id_proveedor");


--
-- Name: rol_permiso rol_permiso_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."rol_permiso"
    ADD CONSTRAINT "rol_permiso_pkey" PRIMARY KEY ("id_rol_permiso");


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."roles"
    ADD CONSTRAINT "roles_pkey" PRIMARY KEY ("id_rol");


--
-- Name: series_documentos series_documentos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."series_documentos"
    ADD CONSTRAINT "series_documentos_pkey" PRIMARY KEY ("id_serie");


--
-- Name: tareas tareas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."tareas"
    ADD CONSTRAINT "tareas_pkey" PRIMARY KEY ("id_tarea");


--
-- Name: tipos_iva tipos_iva_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."tipos_iva"
    ADD CONSTRAINT "tipos_iva_pkey" PRIMARY KEY ("id_iva");


--
-- Name: albaranes_cliente uk_albaran_cliente_serie_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."albaranes_cliente"
    ADD CONSTRAINT "uk_albaran_cliente_serie_numero" UNIQUE ("id_serie", "numero");


--
-- Name: albaranes_proveedor uk_albaran_proveedor_serie_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."albaranes_proveedor"
    ADD CONSTRAINT "uk_albaran_proveedor_serie_numero" UNIQUE ("id_serie", "numero");


--
-- Name: almacenes uk_almacen_codigo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."almacenes"
    ADD CONSTRAINT "uk_almacen_codigo" UNIQUE ("codigo_almacen");


--
-- Name: articulos uk_articulo_codigo_barras; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."articulos"
    ADD CONSTRAINT "uk_articulo_codigo_barras" UNIQUE ("codigo_barras");


--
-- Name: api_ausencias uk_ausencia_empresa; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_ausencias"
    ADD CONSTRAINT "uk_ausencia_empresa" UNIQUE ("id", "id_empresa");


--
-- Name: categorias_operarios uk_categoria_operario_descripcion; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."categorias_operarios"
    ADD CONSTRAINT "uk_categoria_operario_descripcion" UNIQUE ("descripcion");


--
-- Name: clientes uk_cliente_codigo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."clientes"
    ADD CONSTRAINT "uk_cliente_codigo" UNIQUE ("codigo_cliente");


--
-- Name: clientes uk_cliente_entidad; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."clientes"
    ADD CONSTRAINT "uk_cliente_entidad" UNIQUE ("id_entidad");


--
-- Name: entidades uk_entidad_nif_cif; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."entidades"
    ADD CONSTRAINT "uk_entidad_nif_cif" UNIQUE ("nif_cif");


--
-- Name: estados_factura uk_estado_factura_descripcion; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."estados_factura"
    ADD CONSTRAINT "uk_estado_factura_descripcion" UNIQUE ("descripcion");


--
-- Name: estados_incidencia uk_estado_incidencia_descripcion; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."estados_incidencia"
    ADD CONSTRAINT "uk_estado_incidencia_descripcion" UNIQUE ("descripcion");


--
-- Name: estados_obra uk_estado_obra_descripcion; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."estados_obra"
    ADD CONSTRAINT "uk_estado_obra_descripcion" UNIQUE ("descripcion");


--
-- Name: estados_tarea uk_estado_tarea_descripcion; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."estados_tarea"
    ADD CONSTRAINT "uk_estado_tarea_descripcion" UNIQUE ("descripcion");


--
-- Name: familias uk_familia_descripcion; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."familias"
    ADD CONSTRAINT "uk_familia_descripcion" UNIQUE ("descripcion");


--
-- Name: facturas_cliente uk_fc_serie_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."facturas_cliente"
    ADD CONSTRAINT "uk_fc_serie_numero" UNIQUE ("id_serie", "numero");


--
-- Name: formas_pago uk_forma_pago_descripcion; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."formas_pago"
    ADD CONSTRAINT "uk_forma_pago_descripcion" UNIQUE ("descripcion");


--
-- Name: facturas_proveedor uk_fp_serie_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."facturas_proveedor"
    ADD CONSTRAINT "uk_fp_serie_numero" UNIQUE ("id_serie", "numero");


--
-- Name: tipos_iva uk_iva_porcentaje; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."tipos_iva"
    ADD CONSTRAINT "uk_iva_porcentaje" UNIQUE ("porcentaje");


--
-- Name: obras uk_obra_codigo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."obras"
    ADD CONSTRAINT "uk_obra_codigo" UNIQUE ("codigo_obra");


--
-- Name: obra_contacto uk_obra_contacto_rol; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."obra_contacto"
    ADD CONSTRAINT "uk_obra_contacto_rol" UNIQUE ("id_obra", "id_contacto", "rol_en_obra");


--
-- Name: api_obra_empresa uk_obra_empresa; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_obra_empresa"
    ADD CONSTRAINT "uk_obra_empresa" UNIQUE ("id_obra", "id_empresa");


--
-- Name: operarios uk_operario_codigo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."operarios"
    ADD CONSTRAINT "uk_operario_codigo" UNIQUE ("codigo_operario");


--
-- Name: api_operario_empresa uk_operario_empresa; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_operario_empresa"
    ADD CONSTRAINT "uk_operario_empresa" UNIQUE ("id_operario", "id_empresa");


--
-- Name: operarios uk_operario_entidad; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."operarios"
    ADD CONSTRAINT "uk_operario_entidad" UNIQUE ("id_entidad");


--
-- Name: operarios uk_operario_nss; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."operarios"
    ADD CONSTRAINT "uk_operario_nss" UNIQUE ("nss");


--
-- Name: pedidos_proveedor uk_pedido_serie_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."pedidos_proveedor"
    ADD CONSTRAINT "uk_pedido_serie_numero" UNIQUE ("id_serie", "numero");


--
-- Name: permisos uk_permiso_nombre; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."permisos"
    ADD CONSTRAINT "uk_permiso_nombre" UNIQUE ("nombre");


--
-- Name: api_presupuesto_empresa uk_presupuesto_empresa; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_presupuesto_empresa"
    ADD CONSTRAINT "uk_presupuesto_empresa" UNIQUE ("id_presupuesto", "id_empresa");


--
-- Name: presupuestos uk_presupuesto_serie_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."presupuestos"
    ADD CONSTRAINT "uk_presupuesto_serie_numero" UNIQUE ("id_serie", "numero");


--
-- Name: prioridades uk_prioridad_descripcion; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."prioridades"
    ADD CONSTRAINT "uk_prioridad_descripcion" UNIQUE ("descripcion");


--
-- Name: proveedores uk_proveedor_codigo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."proveedores"
    ADD CONSTRAINT "uk_proveedor_codigo" UNIQUE ("codigo_proveedor");


--
-- Name: proveedores uk_proveedor_entidad; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."proveedores"
    ADD CONSTRAINT "uk_proveedor_entidad" UNIQUE ("id_entidad");


--
-- Name: roles uk_rol_nombre; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."roles"
    ADD CONSTRAINT "uk_rol_nombre" UNIQUE ("nombre");


--
-- Name: rol_permiso uk_rol_permiso; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."rol_permiso"
    ADD CONSTRAINT "uk_rol_permiso" UNIQUE ("id_rol", "id_permiso");


--
-- Name: series_documentos uk_serie_propietaria; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."series_documentos"
    ADD CONSTRAINT "uk_serie_propietaria" UNIQUE ("id_serie", "id_empresa");


--
-- Name: unidades_medida uk_unidad_abreviatura; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."unidades_medida"
    ADD CONSTRAINT "uk_unidad_abreviatura" UNIQUE ("abreviatura");


--
-- Name: usuarios uk_usuario_email_login; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."usuarios"
    ADD CONSTRAINT "uk_usuario_email_login" UNIQUE ("email_login");


--
-- Name: usuarios uk_usuario_nombre; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."usuarios"
    ADD CONSTRAINT "uk_usuario_nombre" UNIQUE ("nombre_usuario");


--
-- Name: usuario_rol uk_usuario_rol; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."usuario_rol"
    ADD CONSTRAINT "uk_usuario_rol" UNIQUE ("id_usuario", "id_rol");


--
-- Name: vehiculos uk_vehiculo_matricula; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."vehiculos"
    ADD CONSTRAINT "uk_vehiculo_matricula" UNIQUE ("matricula");


--
-- Name: zonas uk_zona_descripcion; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."zonas"
    ADD CONSTRAINT "uk_zona_descripcion" UNIQUE ("descripcion");


--
-- Name: unidades_medida unidades_medida_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."unidades_medida"
    ADD CONSTRAINT "unidades_medida_pkey" PRIMARY KEY ("id_unidad");


--
-- Name: usuario_rol usuario_rol_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."usuario_rol"
    ADD CONSTRAINT "usuario_rol_pkey" PRIMARY KEY ("id_usuario_rol");


--
-- Name: usuarios usuarios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."usuarios"
    ADD CONSTRAINT "usuarios_pkey" PRIMARY KEY ("id_usuario");


--
-- Name: vehiculos vehiculos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."vehiculos"
    ADD CONSTRAINT "vehiculos_pkey" PRIMARY KEY ("id_vehiculo");


--
-- Name: zonas zonas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."zonas"
    ADD CONSTRAINT "zonas_pkey" PRIMARY KEY ("id_zona");


--
-- Name: idx_albaran_cliente_cliente; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_albaran_cliente_cliente" ON "public"."albaranes_cliente" USING "btree" ("id_cliente");


--
-- Name: idx_albaran_cliente_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_albaran_cliente_empresa" ON "public"."api_albaran_cliente_empresa" USING "btree" ("id_empresa", "id_albaran");


--
-- Name: idx_albaran_cliente_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_albaran_cliente_estado" ON "public"."albaranes_cliente" USING "btree" ("id_estado");


--
-- Name: idx_albaran_cliente_factura; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_albaran_cliente_factura" ON "public"."albaranes_cliente" USING "btree" ("id_factura");


--
-- Name: idx_albaran_cliente_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_albaran_cliente_fecha" ON "public"."albaranes_cliente" USING "btree" ("fecha");


--
-- Name: idx_albaran_cliente_numero; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_albaran_cliente_numero" ON "public"."albaranes_cliente" USING "btree" ("numero");


--
-- Name: idx_albaran_cliente_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_albaran_cliente_usuario_creacion" ON "public"."albaranes_cliente" USING "btree" ("usuario_creacion");


--
-- Name: idx_albaran_cliente_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_albaran_cliente_usuario_modificacion" ON "public"."albaranes_cliente" USING "btree" ("usuario_modificacion");


--
-- Name: idx_albaran_proveedor_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_albaran_proveedor_empresa" ON "public"."api_albaran_proveedor_empresa" USING "btree" ("id_empresa", "id_albaran");


--
-- Name: idx_albaran_proveedor_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_albaran_proveedor_estado" ON "public"."albaranes_proveedor" USING "btree" ("id_estado");


--
-- Name: idx_albaran_proveedor_factura; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_albaran_proveedor_factura" ON "public"."albaranes_proveedor" USING "btree" ("id_factura");


--
-- Name: idx_albaran_proveedor_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_albaran_proveedor_fecha" ON "public"."albaranes_proveedor" USING "btree" ("fecha");


--
-- Name: idx_albaran_proveedor_numero; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_albaran_proveedor_numero" ON "public"."albaranes_proveedor" USING "btree" ("numero");


--
-- Name: idx_albaran_proveedor_proveedor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_albaran_proveedor_proveedor" ON "public"."albaranes_proveedor" USING "btree" ("id_proveedor");


--
-- Name: idx_albaran_proveedor_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_albaran_proveedor_usuario_creacion" ON "public"."albaranes_proveedor" USING "btree" ("usuario_creacion");


--
-- Name: idx_albaran_proveedor_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_albaran_proveedor_usuario_modificacion" ON "public"."albaranes_proveedor" USING "btree" ("usuario_modificacion");


--
-- Name: idx_almacen_descripcion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_almacen_descripcion" ON "public"."almacenes" USING "btree" ("descripcion");


--
-- Name: idx_almacen_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_almacen_empresa" ON "public"."api_almacen_empresa" USING "btree" ("id_empresa", "id_almacen");


--
-- Name: idx_almacen_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_almacen_usuario_creacion" ON "public"."almacenes" USING "btree" ("usuario_creacion");


--
-- Name: idx_almacen_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_almacen_usuario_modificacion" ON "public"."almacenes" USING "btree" ("usuario_modificacion");


--
-- Name: idx_articulo_descripcion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_articulo_descripcion" ON "public"."articulos" USING "btree" ("descripcion");


--
-- Name: idx_articulo_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_articulo_empresa" ON "public"."api_articulo_empresa" USING "btree" ("id_empresa", "referencia_articulo");


--
-- Name: idx_articulo_familia; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_articulo_familia" ON "public"."articulos" USING "btree" ("id_familia");


--
-- Name: idx_articulo_iva; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_articulo_iva" ON "public"."articulos" USING "btree" ("id_iva");


--
-- Name: idx_articulo_proveedor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_articulo_proveedor" ON "public"."articulos" USING "btree" ("id_proveedor");


--
-- Name: idx_articulo_unidad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_articulo_unidad" ON "public"."articulos" USING "btree" ("id_unidad");


--
-- Name: idx_articulo_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_articulo_usuario_creacion" ON "public"."articulos" USING "btree" ("usuario_creacion");


--
-- Name: idx_articulo_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_articulo_usuario_modificacion" ON "public"."articulos" USING "btree" ("usuario_modificacion");


--
-- Name: idx_categoria_operario_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_categoria_operario_activo" ON "public"."categorias_operarios" USING "btree" ("activo");


--
-- Name: idx_categoria_operario_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_categoria_operario_usuario_creacion" ON "public"."categorias_operarios" USING "btree" ("usuario_creacion");


--
-- Name: idx_categoria_operario_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_categoria_operario_usuario_modificacion" ON "public"."categorias_operarios" USING "btree" ("usuario_modificacion");


--
-- Name: idx_cliente_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_cliente_empresa" ON "public"."api_cliente_empresa" USING "btree" ("id_empresa", "id_cliente");


--
-- Name: idx_cliente_forma_pago; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_cliente_forma_pago" ON "public"."clientes" USING "btree" ("id_forma_pago");


--
-- Name: idx_cliente_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_cliente_usuario_creacion" ON "public"."clientes" USING "btree" ("usuario_creacion");


--
-- Name: idx_cliente_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_cliente_usuario_modificacion" ON "public"."clientes" USING "btree" ("usuario_modificacion");


--
-- Name: idx_cliente_zona; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_cliente_zona" ON "public"."clientes" USING "btree" ("id_zona");


--
-- Name: idx_cobro_factura; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_cobro_factura" ON "public"."cobros" USING "btree" ("id_factura");


--
-- Name: idx_cobro_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_cobro_fecha" ON "public"."cobros" USING "btree" ("fecha");


--
-- Name: idx_cobro_numero_operacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_cobro_numero_operacion" ON "public"."cobros" USING "btree" ("numero_operacion");


--
-- Name: idx_cobro_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_cobro_usuario_creacion" ON "public"."cobros" USING "btree" ("usuario_creacion");


--
-- Name: idx_cobro_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_cobro_usuario_modificacion" ON "public"."cobros" USING "btree" ("usuario_modificacion");


--
-- Name: idx_contacto_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_contacto_email" ON "public"."contactos" USING "btree" ("email");


--
-- Name: idx_contacto_entidad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_contacto_entidad" ON "public"."contactos" USING "btree" ("id_entidad");


--
-- Name: idx_contacto_nombre; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_contacto_nombre" ON "public"."contactos" USING "btree" ("nombre", "apellidos");


--
-- Name: idx_contacto_principal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_contacto_principal" ON "public"."contactos" USING "btree" ("principal");


--
-- Name: idx_contacto_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_contacto_usuario_creacion" ON "public"."contactos" USING "btree" ("usuario_creacion");


--
-- Name: idx_contacto_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_contacto_usuario_modificacion" ON "public"."contactos" USING "btree" ("usuario_modificacion");


--
-- Name: idx_direccion_codigo_postal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_direccion_codigo_postal" ON "public"."direcciones" USING "btree" ("codigo_postal");


--
-- Name: idx_direccion_entidad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_direccion_entidad" ON "public"."direcciones" USING "btree" ("id_entidad");


--
-- Name: idx_direccion_poblacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_direccion_poblacion" ON "public"."direcciones" USING "btree" ("poblacion");


--
-- Name: idx_direccion_principal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_direccion_principal" ON "public"."direcciones" USING "btree" ("principal");


--
-- Name: idx_direccion_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_direccion_tipo" ON "public"."direcciones" USING "btree" ("tipo_direccion");


--
-- Name: idx_direccion_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_direccion_usuario_creacion" ON "public"."direcciones" USING "btree" ("usuario_creacion");


--
-- Name: idx_direccion_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_direccion_usuario_modificacion" ON "public"."direcciones" USING "btree" ("usuario_modificacion");


--
-- Name: idx_doc_general_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_doc_general_fecha" ON "public"."documentos_generales" USING "btree" ("fecha_subida");


--
-- Name: idx_doc_general_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_doc_general_tipo" ON "public"."documentos_generales" USING "btree" ("tipo");


--
-- Name: idx_doc_general_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_doc_general_usuario" ON "public"."documentos_generales" USING "btree" ("id_usuario");


--
-- Name: idx_doc_general_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_doc_general_usuario_creacion" ON "public"."documentos_generales" USING "btree" ("usuario_creacion");


--
-- Name: idx_doc_general_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_doc_general_usuario_modificacion" ON "public"."documentos_generales" USING "btree" ("usuario_modificacion");


--
-- Name: idx_documento_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_documento_fecha" ON "public"."documentos" USING "btree" ("fecha_subida");


--
-- Name: idx_documento_obra; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_documento_obra" ON "public"."documentos" USING "btree" ("id_obra");


--
-- Name: idx_documento_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_documento_tipo" ON "public"."documentos" USING "btree" ("tipo");


--
-- Name: idx_documento_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_documento_usuario_creacion" ON "public"."documentos" USING "btree" ("usuario_creacion");


--
-- Name: idx_documento_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_documento_usuario_modificacion" ON "public"."documentos" USING "btree" ("usuario_modificacion");


--
-- Name: idx_entidad_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_entidad_activo" ON "public"."entidades" USING "btree" ("activo");


--
-- Name: idx_entidad_nombre; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_entidad_nombre" ON "public"."entidades" USING "btree" ("nombre");


--
-- Name: idx_entidad_nombre_comercial; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_entidad_nombre_comercial" ON "public"."entidades" USING "btree" ("nombre_comercial");


--
-- Name: idx_entidad_razon_social; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_entidad_razon_social" ON "public"."entidades" USING "btree" ("razon_social");


--
-- Name: idx_entidad_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_entidad_tipo" ON "public"."entidades" USING "btree" ("tipo_entidad");


--
-- Name: idx_entidad_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_entidad_usuario_creacion" ON "public"."entidades" USING "btree" ("usuario_creacion");


--
-- Name: idx_entidad_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_entidad_usuario_modificacion" ON "public"."entidades" USING "btree" ("usuario_modificacion");


--
-- Name: idx_estado_factura_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_estado_factura_activo" ON "public"."estados_factura" USING "btree" ("activo");


--
-- Name: idx_estado_factura_orden; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_estado_factura_orden" ON "public"."estados_factura" USING "btree" ("orden_visualizacion");


--
-- Name: idx_estado_factura_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_estado_factura_usuario_creacion" ON "public"."estados_factura" USING "btree" ("usuario_creacion");


--
-- Name: idx_estado_factura_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_estado_factura_usuario_modificacion" ON "public"."estados_factura" USING "btree" ("usuario_modificacion");


--
-- Name: idx_estado_incidencia_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_estado_incidencia_activo" ON "public"."estados_incidencia" USING "btree" ("activo");


--
-- Name: idx_estado_incidencia_orden; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_estado_incidencia_orden" ON "public"."estados_incidencia" USING "btree" ("orden_visualizacion");


--
-- Name: idx_estado_incidencia_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_estado_incidencia_usuario_creacion" ON "public"."estados_incidencia" USING "btree" ("usuario_creacion");


--
-- Name: idx_estado_incidencia_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_estado_incidencia_usuario_modificacion" ON "public"."estados_incidencia" USING "btree" ("usuario_modificacion");


--
-- Name: idx_estado_obra_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_estado_obra_activo" ON "public"."estados_obra" USING "btree" ("activo");


--
-- Name: idx_estado_obra_orden; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_estado_obra_orden" ON "public"."estados_obra" USING "btree" ("orden_visualizacion");


--
-- Name: idx_estado_obra_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_estado_obra_usuario_creacion" ON "public"."estados_obra" USING "btree" ("usuario_creacion");


--
-- Name: idx_estado_obra_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_estado_obra_usuario_modificacion" ON "public"."estados_obra" USING "btree" ("usuario_modificacion");


--
-- Name: idx_estado_tarea_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_estado_tarea_activo" ON "public"."estados_tarea" USING "btree" ("activo");


--
-- Name: idx_estado_tarea_orden; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_estado_tarea_orden" ON "public"."estados_tarea" USING "btree" ("orden_visualizacion");


--
-- Name: idx_estado_tarea_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_estado_tarea_usuario_creacion" ON "public"."estados_tarea" USING "btree" ("usuario_creacion");


--
-- Name: idx_estado_tarea_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_estado_tarea_usuario_modificacion" ON "public"."estados_tarea" USING "btree" ("usuario_modificacion");


--
-- Name: idx_evento_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_evento_empresa" ON "public"."api_evento_empresa" USING "btree" ("id_empresa", "id_evento");


--
-- Name: idx_evento_fecha_inicio; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_evento_fecha_inicio" ON "public"."eventos" USING "btree" ("fecha_inicio");


--
-- Name: idx_evento_obra; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_evento_obra" ON "public"."eventos" USING "btree" ("id_obra");


--
-- Name: idx_evento_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_evento_tipo" ON "public"."eventos" USING "btree" ("tipo_evento");


--
-- Name: idx_evento_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_evento_usuario_creacion" ON "public"."eventos" USING "btree" ("usuario_creacion");


--
-- Name: idx_evento_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_evento_usuario_modificacion" ON "public"."eventos" USING "btree" ("usuario_modificacion");


--
-- Name: idx_factura_cliente_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_factura_cliente_empresa" ON "public"."api_factura_cliente_empresa" USING "btree" ("id_empresa", "id_factura");


--
-- Name: idx_factura_proveedor_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_factura_proveedor_empresa" ON "public"."api_factura_proveedor_empresa" USING "btree" ("id_empresa", "id_factura");


--
-- Name: idx_familia_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_familia_activo" ON "public"."familias" USING "btree" ("activo");


--
-- Name: idx_familia_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_familia_usuario_creacion" ON "public"."familias" USING "btree" ("usuario_creacion");


--
-- Name: idx_familia_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_familia_usuario_modificacion" ON "public"."familias" USING "btree" ("usuario_modificacion");


--
-- Name: idx_fc_cliente; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_fc_cliente" ON "public"."facturas_cliente" USING "btree" ("id_cliente");


--
-- Name: idx_fc_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_fc_estado" ON "public"."facturas_cliente" USING "btree" ("id_estado");


--
-- Name: idx_fc_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_fc_fecha" ON "public"."facturas_cliente" USING "btree" ("fecha");


--
-- Name: idx_fc_numero; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_fc_numero" ON "public"."facturas_cliente" USING "btree" ("numero");


--
-- Name: idx_fc_obra; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_fc_obra" ON "public"."facturas_cliente" USING "btree" ("id_obra");


--
-- Name: idx_fc_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_fc_usuario_creacion" ON "public"."facturas_cliente" USING "btree" ("usuario_creacion");


--
-- Name: idx_fc_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_fc_usuario_modificacion" ON "public"."facturas_cliente" USING "btree" ("usuario_modificacion");


--
-- Name: idx_forma_pago_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_forma_pago_activo" ON "public"."formas_pago" USING "btree" ("activo");


--
-- Name: idx_forma_pago_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_forma_pago_usuario_creacion" ON "public"."formas_pago" USING "btree" ("usuario_creacion");


--
-- Name: idx_forma_pago_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_forma_pago_usuario_modificacion" ON "public"."formas_pago" USING "btree" ("usuario_modificacion");


--
-- Name: idx_fp_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_fp_estado" ON "public"."facturas_proveedor" USING "btree" ("id_estado");


--
-- Name: idx_fp_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_fp_fecha" ON "public"."facturas_proveedor" USING "btree" ("fecha");


--
-- Name: idx_fp_numero; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_fp_numero" ON "public"."facturas_proveedor" USING "btree" ("numero");


--
-- Name: idx_fp_proveedor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_fp_proveedor" ON "public"."facturas_proveedor" USING "btree" ("id_proveedor");


--
-- Name: idx_fp_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_fp_usuario_creacion" ON "public"."facturas_proveedor" USING "btree" ("usuario_creacion");


--
-- Name: idx_fp_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_fp_usuario_modificacion" ON "public"."facturas_proveedor" USING "btree" ("usuario_modificacion");


--
-- Name: idx_historial_estado_anterior; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_historial_estado_anterior" ON "public"."historial_estados_obra" USING "btree" ("id_estado_anterior");


--
-- Name: idx_historial_estado_nuevo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_historial_estado_nuevo" ON "public"."historial_estados_obra" USING "btree" ("id_estado_nuevo");


--
-- Name: idx_historial_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_historial_fecha" ON "public"."historial_estados_obra" USING "btree" ("fecha");


--
-- Name: idx_historial_obra; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_historial_obra" ON "public"."historial_estados_obra" USING "btree" ("id_obra");


--
-- Name: idx_historial_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_historial_usuario" ON "public"."historial_estados_obra" USING "btree" ("id_usuario");


--
-- Name: idx_historial_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_historial_usuario_creacion" ON "public"."historial_estados_obra" USING "btree" ("usuario_creacion");


--
-- Name: idx_historial_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_historial_usuario_modificacion" ON "public"."historial_estados_obra" USING "btree" ("usuario_modificacion");


--
-- Name: idx_incidencia_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_incidencia_empresa" ON "public"."api_incidencia_empresa" USING "btree" ("id_empresa", "id_incidencia");


--
-- Name: idx_incidencia_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_incidencia_estado" ON "public"."incidencias" USING "btree" ("id_estado");


--
-- Name: idx_incidencia_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_incidencia_fecha" ON "public"."incidencias" USING "btree" ("fecha");


--
-- Name: idx_incidencia_obra; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_incidencia_obra" ON "public"."incidencias" USING "btree" ("id_obra");


--
-- Name: idx_incidencia_operario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_incidencia_operario" ON "public"."incidencias" USING "btree" ("id_operario");


--
-- Name: idx_incidencia_prioridad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_incidencia_prioridad" ON "public"."incidencias" USING "btree" ("id_prioridad");


--
-- Name: idx_incidencia_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_incidencia_usuario_creacion" ON "public"."incidencias" USING "btree" ("usuario_creacion");


--
-- Name: idx_incidencia_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_incidencia_usuario_modificacion" ON "public"."incidencias" USING "btree" ("usuario_modificacion");


--
-- Name: idx_iva_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_iva_activo" ON "public"."tipos_iva" USING "btree" ("activo");


--
-- Name: idx_iva_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_iva_usuario_creacion" ON "public"."tipos_iva" USING "btree" ("usuario_creacion");


--
-- Name: idx_iva_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_iva_usuario_modificacion" ON "public"."tipos_iva" USING "btree" ("usuario_modificacion");


--
-- Name: idx_lac_albaran; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lac_albaran" ON "public"."lineas_albaran_cliente" USING "btree" ("id_albaran");


--
-- Name: idx_lac_articulo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lac_articulo" ON "public"."lineas_albaran_cliente" USING "btree" ("referencia_articulo");


--
-- Name: idx_lac_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lac_usuario_creacion" ON "public"."lineas_albaran_cliente" USING "btree" ("usuario_creacion");


--
-- Name: idx_lac_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lac_usuario_modificacion" ON "public"."lineas_albaran_cliente" USING "btree" ("usuario_modificacion");


--
-- Name: idx_lap_albaran; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lap_albaran" ON "public"."lineas_albaran_proveedor" USING "btree" ("id_albaran");


--
-- Name: idx_lap_articulo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lap_articulo" ON "public"."lineas_albaran_proveedor" USING "btree" ("referencia_articulo");


--
-- Name: idx_lap_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lap_usuario_creacion" ON "public"."lineas_albaran_proveedor" USING "btree" ("usuario_creacion");


--
-- Name: idx_lap_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lap_usuario_modificacion" ON "public"."lineas_albaran_proveedor" USING "btree" ("usuario_modificacion");


--
-- Name: idx_lfc_articulo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lfc_articulo" ON "public"."lineas_factura_cliente" USING "btree" ("referencia_articulo");


--
-- Name: idx_lfc_factura; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lfc_factura" ON "public"."lineas_factura_cliente" USING "btree" ("id_factura");


--
-- Name: idx_lfc_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lfc_usuario_creacion" ON "public"."lineas_factura_cliente" USING "btree" ("usuario_creacion");


--
-- Name: idx_lfc_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lfc_usuario_modificacion" ON "public"."lineas_factura_cliente" USING "btree" ("usuario_modificacion");


--
-- Name: idx_lfp_articulo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lfp_articulo" ON "public"."lineas_factura_proveedor" USING "btree" ("referencia_articulo");


--
-- Name: idx_lfp_factura; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lfp_factura" ON "public"."lineas_factura_proveedor" USING "btree" ("id_factura");


--
-- Name: idx_lfp_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lfp_usuario_creacion" ON "public"."lineas_factura_proveedor" USING "btree" ("usuario_creacion");


--
-- Name: idx_lfp_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lfp_usuario_modificacion" ON "public"."lineas_factura_proveedor" USING "btree" ("usuario_modificacion");


--
-- Name: idx_linea_pedido; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_linea_pedido" ON "public"."lineas_pedido" USING "btree" ("id_pedido");


--
-- Name: idx_linea_pedido_articulo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_linea_pedido_articulo" ON "public"."lineas_pedido" USING "btree" ("referencia_articulo");


--
-- Name: idx_linea_pedido_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_linea_pedido_usuario_creacion" ON "public"."lineas_pedido" USING "btree" ("usuario_creacion");


--
-- Name: idx_linea_pedido_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_linea_pedido_usuario_modificacion" ON "public"."lineas_pedido" USING "btree" ("usuario_modificacion");


--
-- Name: idx_log_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_log_fecha" ON "public"."log" USING "btree" ("fecha");


--
-- Name: idx_log_tabla; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_log_tabla" ON "public"."log" USING "btree" ("tabla_afectada");


--
-- Name: idx_log_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_log_usuario" ON "public"."log" USING "btree" ("id_usuario");


--
-- Name: idx_lp_articulo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lp_articulo" ON "public"."lineas_presupuesto" USING "btree" ("referencia_articulo");


--
-- Name: idx_lp_presupuesto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lp_presupuesto" ON "public"."lineas_presupuesto" USING "btree" ("id_presupuesto");


--
-- Name: idx_lp_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lp_usuario_creacion" ON "public"."lineas_presupuesto" USING "btree" ("usuario_creacion");


--
-- Name: idx_lp_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_lp_usuario_modificacion" ON "public"."lineas_presupuesto" USING "btree" ("usuario_modificacion");


--
-- Name: idx_material_almacen; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_material_almacen" ON "public"."materiales_por_obra" USING "btree" ("id_almacen");


--
-- Name: idx_material_articulo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_material_articulo" ON "public"."materiales_por_obra" USING "btree" ("referencia_articulo");


--
-- Name: idx_material_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_material_fecha" ON "public"."materiales_por_obra" USING "btree" ("fecha");


--
-- Name: idx_material_obra; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_material_obra" ON "public"."materiales_por_obra" USING "btree" ("id_obra");


--
-- Name: idx_material_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_material_usuario_creacion" ON "public"."materiales_por_obra" USING "btree" ("usuario_creacion");


--
-- Name: idx_material_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_material_usuario_modificacion" ON "public"."materiales_por_obra" USING "btree" ("usuario_modificacion");


--
-- Name: idx_obra_cliente; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_obra_cliente" ON "public"."obras" USING "btree" ("id_cliente");


--
-- Name: idx_obra_contacto_contacto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_obra_contacto_contacto" ON "public"."obra_contacto" USING "btree" ("id_contacto");


--
-- Name: idx_obra_contacto_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_obra_contacto_usuario_creacion" ON "public"."obra_contacto" USING "btree" ("usuario_creacion");


--
-- Name: idx_obra_contacto_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_obra_contacto_usuario_modificacion" ON "public"."obra_contacto" USING "btree" ("usuario_modificacion");


--
-- Name: idx_obra_direccion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_obra_direccion" ON "public"."obras" USING "btree" ("id_direccion");


--
-- Name: idx_obra_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_obra_empresa" ON "public"."api_obra_empresa" USING "btree" ("id_empresa", "id_obra");


--
-- Name: idx_obra_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_obra_estado" ON "public"."obras" USING "btree" ("id_estado");


--
-- Name: idx_obra_fecha_inicio; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_obra_fecha_inicio" ON "public"."obras" USING "btree" ("fecha_inicio");


--
-- Name: idx_obra_serie; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_obra_serie" ON "public"."obras" USING "btree" ("id_serie");


--
-- Name: idx_obra_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_obra_usuario_creacion" ON "public"."obras" USING "btree" ("usuario_creacion");


--
-- Name: idx_obra_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_obra_usuario_modificacion" ON "public"."obras" USING "btree" ("usuario_modificacion");


--
-- Name: idx_operario_categoria; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_operario_categoria" ON "public"."operarios" USING "btree" ("id_categoria");


--
-- Name: idx_operario_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_operario_empresa" ON "public"."api_operario_empresa" USING "btree" ("id_empresa", "id_operario");


--
-- Name: idx_operario_fecha_alta; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_operario_fecha_alta" ON "public"."operarios" USING "btree" ("fecha_alta");


--
-- Name: idx_operario_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_operario_usuario_creacion" ON "public"."operarios" USING "btree" ("usuario_creacion");


--
-- Name: idx_operario_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_operario_usuario_modificacion" ON "public"."operarios" USING "btree" ("usuario_modificacion");


--
-- Name: idx_pago_factura; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_pago_factura" ON "public"."pagos" USING "btree" ("id_factura");


--
-- Name: idx_pago_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_pago_fecha" ON "public"."pagos" USING "btree" ("fecha");


--
-- Name: idx_pago_numero_operacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_pago_numero_operacion" ON "public"."pagos" USING "btree" ("numero_operacion");


--
-- Name: idx_pago_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_pago_usuario_creacion" ON "public"."pagos" USING "btree" ("usuario_creacion");


--
-- Name: idx_pago_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_pago_usuario_modificacion" ON "public"."pagos" USING "btree" ("usuario_modificacion");


--
-- Name: idx_parte_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_parte_fecha" ON "public"."partes_mano_obra" USING "btree" ("fecha");


--
-- Name: idx_parte_obra; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_parte_obra" ON "public"."partes_mano_obra" USING "btree" ("id_obra");


--
-- Name: idx_parte_operario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_parte_operario" ON "public"."partes_mano_obra" USING "btree" ("id_operario");


--
-- Name: idx_parte_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_parte_usuario_creacion" ON "public"."partes_mano_obra" USING "btree" ("usuario_creacion");


--
-- Name: idx_parte_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_parte_usuario_modificacion" ON "public"."partes_mano_obra" USING "btree" ("usuario_modificacion");


--
-- Name: idx_pedido_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_pedido_estado" ON "public"."pedidos_proveedor" USING "btree" ("id_estado");


--
-- Name: idx_pedido_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_pedido_fecha" ON "public"."pedidos_proveedor" USING "btree" ("fecha");


--
-- Name: idx_pedido_numero; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_pedido_numero" ON "public"."pedidos_proveedor" USING "btree" ("numero");


--
-- Name: idx_pedido_proveedor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_pedido_proveedor" ON "public"."pedidos_proveedor" USING "btree" ("id_proveedor");


--
-- Name: idx_pedido_proveedor_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_pedido_proveedor_empresa" ON "public"."api_pedido_proveedor_empresa" USING "btree" ("id_empresa", "id_pedido");


--
-- Name: idx_pedido_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_pedido_usuario_creacion" ON "public"."pedidos_proveedor" USING "btree" ("usuario_creacion");


--
-- Name: idx_pedido_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_pedido_usuario_modificacion" ON "public"."pedidos_proveedor" USING "btree" ("usuario_modificacion");


--
-- Name: idx_permiso_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_permiso_activo" ON "public"."permisos" USING "btree" ("activo");


--
-- Name: idx_permiso_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_permiso_usuario_creacion" ON "public"."permisos" USING "btree" ("usuario_creacion");


--
-- Name: idx_permiso_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_permiso_usuario_modificacion" ON "public"."permisos" USING "btree" ("usuario_modificacion");


--
-- Name: idx_presupuesto_cliente; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_presupuesto_cliente" ON "public"."presupuestos" USING "btree" ("id_cliente");


--
-- Name: idx_presupuesto_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_presupuesto_empresa" ON "public"."api_presupuesto_empresa" USING "btree" ("id_empresa", "id_presupuesto");


--
-- Name: idx_presupuesto_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_presupuesto_estado" ON "public"."presupuestos" USING "btree" ("id_estado");


--
-- Name: idx_presupuesto_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_presupuesto_fecha" ON "public"."presupuestos" USING "btree" ("fecha");


--
-- Name: idx_presupuesto_numero; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_presupuesto_numero" ON "public"."presupuestos" USING "btree" ("numero");


--
-- Name: idx_presupuesto_obra; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_presupuesto_obra" ON "public"."presupuestos" USING "btree" ("id_obra");


--
-- Name: idx_presupuesto_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_presupuesto_usuario_creacion" ON "public"."presupuestos" USING "btree" ("usuario_creacion");


--
-- Name: idx_presupuesto_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_presupuesto_usuario_modificacion" ON "public"."presupuestos" USING "btree" ("usuario_modificacion");


--
-- Name: idx_prioridad_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_prioridad_activo" ON "public"."prioridades" USING "btree" ("activo");


--
-- Name: idx_prioridad_orden; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_prioridad_orden" ON "public"."prioridades" USING "btree" ("orden_visualizacion");


--
-- Name: idx_prioridad_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_prioridad_usuario_creacion" ON "public"."prioridades" USING "btree" ("usuario_creacion");


--
-- Name: idx_prioridad_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_prioridad_usuario_modificacion" ON "public"."prioridades" USING "btree" ("usuario_modificacion");


--
-- Name: idx_proveedor_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_proveedor_empresa" ON "public"."api_proveedor_empresa" USING "btree" ("id_empresa", "id_proveedor");


--
-- Name: idx_proveedor_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_proveedor_tipo" ON "public"."proveedores" USING "btree" ("tipo_proveedor");


--
-- Name: idx_proveedor_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_proveedor_usuario_creacion" ON "public"."proveedores" USING "btree" ("usuario_creacion");


--
-- Name: idx_proveedor_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_proveedor_usuario_modificacion" ON "public"."proveedores" USING "btree" ("usuario_modificacion");


--
-- Name: idx_rol_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_rol_activo" ON "public"."roles" USING "btree" ("activo");


--
-- Name: idx_rol_permiso_permiso; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_rol_permiso_permiso" ON "public"."rol_permiso" USING "btree" ("id_permiso");


--
-- Name: idx_rol_permiso_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_rol_permiso_usuario_creacion" ON "public"."rol_permiso" USING "btree" ("usuario_creacion");


--
-- Name: idx_rol_permiso_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_rol_permiso_usuario_modificacion" ON "public"."rol_permiso" USING "btree" ("usuario_modificacion");


--
-- Name: idx_rol_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_rol_usuario_creacion" ON "public"."roles" USING "btree" ("usuario_creacion");


--
-- Name: idx_rol_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_rol_usuario_modificacion" ON "public"."roles" USING "btree" ("usuario_modificacion");


--
-- Name: idx_serie_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_serie_activo" ON "public"."series_documentos" USING "btree" ("activo");


--
-- Name: idx_serie_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_serie_usuario_creacion" ON "public"."series_documentos" USING "btree" ("usuario_creacion");


--
-- Name: idx_serie_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_serie_usuario_modificacion" ON "public"."series_documentos" USING "btree" ("usuario_modificacion");


--
-- Name: idx_sesion_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_sesion_usuario" ON "public"."api_sesiones" USING "btree" ("id_usuario");


--
-- Name: idx_stock_almacen; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_stock_almacen" ON "public"."movimientos_stock" USING "btree" ("id_almacen");


--
-- Name: idx_stock_articulo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_stock_articulo" ON "public"."movimientos_stock" USING "btree" ("referencia_articulo");


--
-- Name: idx_stock_documento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_stock_documento" ON "public"."movimientos_stock" USING "btree" ("documento");


--
-- Name: idx_stock_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_stock_fecha" ON "public"."movimientos_stock" USING "btree" ("fecha");


--
-- Name: idx_stock_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_stock_usuario_creacion" ON "public"."movimientos_stock" USING "btree" ("usuario_creacion");


--
-- Name: idx_stock_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_stock_usuario_modificacion" ON "public"."movimientos_stock" USING "btree" ("usuario_modificacion");


--
-- Name: idx_tarea_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_tarea_empresa" ON "public"."api_tarea_empresa" USING "btree" ("id_empresa", "id_tarea");


--
-- Name: idx_tarea_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_tarea_estado" ON "public"."tareas" USING "btree" ("id_estado");


--
-- Name: idx_tarea_fecha_limite; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_tarea_fecha_limite" ON "public"."tareas" USING "btree" ("fecha_limite");


--
-- Name: idx_tarea_obra; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_tarea_obra" ON "public"."tareas" USING "btree" ("id_obra");


--
-- Name: idx_tarea_operario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_tarea_operario" ON "public"."tareas" USING "btree" ("id_operario");


--
-- Name: idx_tarea_prioridad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_tarea_prioridad" ON "public"."tareas" USING "btree" ("id_prioridad");


--
-- Name: idx_tarea_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_tarea_usuario_creacion" ON "public"."tareas" USING "btree" ("usuario_creacion");


--
-- Name: idx_tarea_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_tarea_usuario_modificacion" ON "public"."tareas" USING "btree" ("usuario_modificacion");


--
-- Name: idx_unidad_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_unidad_activo" ON "public"."unidades_medida" USING "btree" ("activo");


--
-- Name: idx_unidad_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_unidad_usuario_creacion" ON "public"."unidades_medida" USING "btree" ("usuario_creacion");


--
-- Name: idx_unidad_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_unidad_usuario_modificacion" ON "public"."unidades_medida" USING "btree" ("usuario_modificacion");


--
-- Name: idx_usuario_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_usuario_activo" ON "public"."usuarios" USING "btree" ("activo");


--
-- Name: idx_usuario_entidad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_usuario_entidad" ON "public"."usuarios" USING "btree" ("id_entidad");


--
-- Name: idx_usuario_rol_rol; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_usuario_rol_rol" ON "public"."usuario_rol" USING "btree" ("id_rol");


--
-- Name: idx_usuario_rol_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_usuario_rol_usuario_creacion" ON "public"."usuario_rol" USING "btree" ("usuario_creacion");


--
-- Name: idx_usuario_rol_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_usuario_rol_usuario_modificacion" ON "public"."usuario_rol" USING "btree" ("usuario_modificacion");


--
-- Name: idx_usuario_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_usuario_usuario_creacion" ON "public"."usuarios" USING "btree" ("usuario_creacion");


--
-- Name: idx_usuario_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_usuario_usuario_modificacion" ON "public"."usuarios" USING "btree" ("usuario_modificacion");


--
-- Name: idx_vehiculo_fecha_itv; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_vehiculo_fecha_itv" ON "public"."vehiculos" USING "btree" ("fecha_itv");


--
-- Name: idx_vehiculo_marca; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_vehiculo_marca" ON "public"."vehiculos" USING "btree" ("marca");


--
-- Name: idx_vehiculo_modelo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_vehiculo_modelo" ON "public"."vehiculos" USING "btree" ("modelo");


--
-- Name: idx_vehiculo_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_vehiculo_usuario_creacion" ON "public"."vehiculos" USING "btree" ("usuario_creacion");


--
-- Name: idx_vehiculo_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_vehiculo_usuario_modificacion" ON "public"."vehiculos" USING "btree" ("usuario_modificacion");


--
-- Name: idx_zona_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_zona_activo" ON "public"."zonas" USING "btree" ("activo");


--
-- Name: idx_zona_usuario_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_zona_usuario_creacion" ON "public"."zonas" USING "btree" ("usuario_creacion");


--
-- Name: idx_zona_usuario_modificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_zona_usuario_modificacion" ON "public"."zonas" USING "btree" ("usuario_modificacion");


--
-- Name: ix_asignaciones_obra; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ix_asignaciones_obra" ON "public"."api_asignaciones_obra" USING "btree" ("id_empresa", "id_obra");


--
-- Name: ix_asignaciones_operario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ix_asignaciones_operario" ON "public"."api_asignaciones_obra" USING "btree" ("id_empresa", "id_operario", "desde", "id");


--
-- Name: ix_auditoria_autor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ix_auditoria_autor" ON "public"."api_auditoria" USING "btree" ("autor", "fecha" DESC, "id" DESC);


--
-- Name: ix_auditoria_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ix_auditoria_empresa" ON "public"."api_auditoria" USING "btree" ("empresa", "fecha" DESC, "id" DESC);


--
-- Name: ix_auditoria_entidad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ix_auditoria_entidad" ON "public"."api_auditoria" USING "btree" ("modulo", "entidad", "entidad_id", "id" DESC);


--
-- Name: ix_auditoria_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ix_auditoria_fecha" ON "public"."api_auditoria" USING "btree" ("fecha" DESC, "id" DESC);


--
-- Name: ix_ausencias_operario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ix_ausencias_operario" ON "public"."api_ausencias" USING "btree" ("id_empresa", "id_operario", "desde", "id");


--
-- Name: ix_documentos_ausencia; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ix_documentos_ausencia" ON "public"."api_documentos" USING "btree" ("id_empresa", "ausencia", "id");


--
-- Name: ix_documentos_obra; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ix_documentos_obra" ON "public"."api_documentos" USING "btree" ("id_empresa", "obra", "id");


--
-- Name: ix_documentos_presupuesto; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ix_documentos_presupuesto" ON "public"."api_documentos" USING "btree" ("id_empresa", "presupuesto", "id");


--
-- Name: ix_facturacion_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ix_facturacion_empresa" ON "public"."api_facturacion" USING "btree" ("id_empresa", "tipo", "id");


--
-- Name: ix_laboral_auditoria_operario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ix_laboral_auditoria_operario" ON "public"."api_laboral_auditoria" USING "btree" ("empresa", "operario", "id");


--
-- Name: ix_observaciones_obra; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ix_observaciones_obra" ON "public"."api_observaciones_obra" USING "btree" ("id_empresa", "id_obra", "id");


--
-- Name: ix_usuarios_auditoria_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ix_usuarios_auditoria_usuario" ON "public"."api_usuarios_auditoria" USING "btree" ("usuario", "id");


--
-- Name: ix_vencimiento_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "ix_vencimiento_fecha" ON "public"."api_vencimientos" USING "btree" ("fecha", "id");


--
-- Name: uk_factura_referencia; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "uk_factura_referencia" ON "public"."api_facturacion" USING "btree" ("id_empresa", "proveedor", "referencia_externa") WHERE ((("estado")::"text" <> 'ANULADA'::"text") AND (("tipo")::"text" = 'PROVEEDOR'::"text"));


--
-- Name: uk_prefijo_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "uk_prefijo_empresa" ON "public"."series_documentos" USING "btree" ("id_empresa", "tipo_documento", "ejercicio", "prefijo") WHERE ("id_empresa" IS NOT NULL);


--
-- Name: uk_rectificacion_vigente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "uk_rectificacion_vigente" ON "public"."api_facturacion" USING "btree" ("original") WHERE (("estado")::"text" <> 'ANULADA'::"text");


--
-- Name: uk_serie_empresa; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "uk_serie_empresa" ON "public"."series_documentos" USING "btree" ("id_empresa", "tipo_documento", "ejercicio", "nombre") WHERE ("id_empresa" IS NOT NULL);


--
-- Name: uk_serie_historica; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "uk_serie_historica" ON "public"."series_documentos" USING "btree" ("tipo_documento", "ejercicio", "nombre") WHERE ("id_empresa" IS NULL);


--
-- Name: albaranes_cliente actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."albaranes_cliente" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: albaranes_proveedor actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."albaranes_proveedor" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: almacenes actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."almacenes" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: articulos actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."articulos" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: categorias_operarios actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."categorias_operarios" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: clientes actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."clientes" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: cobros actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."cobros" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: contactos actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."contactos" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: direcciones actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."direcciones" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: documentos actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."documentos" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: documentos_generales actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."documentos_generales" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: entidades actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."entidades" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: estados_factura actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."estados_factura" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: estados_incidencia actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."estados_incidencia" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: estados_obra actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."estados_obra" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: estados_tarea actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."estados_tarea" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: eventos actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."eventos" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: facturas_cliente actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."facturas_cliente" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: facturas_proveedor actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."facturas_proveedor" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: familias actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."familias" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: formas_pago actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."formas_pago" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: historial_estados_obra actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."historial_estados_obra" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: incidencias actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."incidencias" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: lineas_albaran_cliente actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."lineas_albaran_cliente" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: lineas_albaran_proveedor actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."lineas_albaran_proveedor" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: lineas_factura_cliente actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."lineas_factura_cliente" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: lineas_factura_proveedor actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."lineas_factura_proveedor" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: lineas_pedido actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."lineas_pedido" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: lineas_presupuesto actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."lineas_presupuesto" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: materiales_por_obra actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."materiales_por_obra" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: movimientos_stock actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."movimientos_stock" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: obra_contacto actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."obra_contacto" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: obras actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."obras" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: operarios actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."operarios" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: pagos actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."pagos" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: partes_mano_obra actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."partes_mano_obra" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: pedidos_proveedor actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."pedidos_proveedor" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: permisos actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."permisos" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: presupuestos actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."presupuestos" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: prioridades actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."prioridades" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: proveedores actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."proveedores" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: rol_permiso actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."rol_permiso" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: roles actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."roles" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: series_documentos actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."series_documentos" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: tareas actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."tareas" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: tipos_iva actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."tipos_iva" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: unidades_medida actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."unidades_medida" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: usuario_rol actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."usuario_rol" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: usuarios actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."usuarios" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: vehiculos actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."vehiculos" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: zonas actualizar_fecha_modificacion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "actualizar_fecha_modificacion" BEFORE UPDATE ON "public"."zonas" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_actualizar_fecha_modificacion"();


--
-- Name: api_auditoria auditoria_inmutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "auditoria_inmutable" BEFORE DELETE OR UPDATE OR TRUNCATE ON "public"."api_auditoria" FOR EACH STATEMENT EXECUTE FUNCTION "public"."dorna_auditoria_inmutable"();


--
-- Name: api_documentos_auditoria consolidar_auditoria; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "consolidar_auditoria" AFTER INSERT ON "public"."api_documentos_auditoria" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_auditoria_evento"();


--
-- Name: api_facturacion_auditoria consolidar_auditoria; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "consolidar_auditoria" AFTER INSERT ON "public"."api_facturacion_auditoria" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_auditoria_evento"();


--
-- Name: api_gestion_auditoria consolidar_auditoria; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "consolidar_auditoria" AFTER INSERT ON "public"."api_gestion_auditoria" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_gestion_auditoria_evento"();


--
-- Name: api_laboral_auditoria consolidar_auditoria; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "consolidar_auditoria" AFTER INSERT ON "public"."api_laboral_auditoria" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_auditoria_evento"();


--
-- Name: api_observaciones_auditoria consolidar_auditoria; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "consolidar_auditoria" AFTER INSERT ON "public"."api_observaciones_auditoria" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_auditoria_evento"();


--
-- Name: api_tesoreria_auditoria consolidar_auditoria; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "consolidar_auditoria" AFTER INSERT ON "public"."api_tesoreria_auditoria" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_auditoria_evento"();


--
-- Name: api_usuarios_auditoria consolidar_auditoria; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "consolidar_auditoria" AFTER INSERT ON "public"."api_usuarios_auditoria" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_auditoria_evento"();


--
-- Name: api_visibilidad_auditoria consolidar_auditoria; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "consolidar_auditoria" AFTER INSERT ON "public"."api_visibilidad_auditoria" FOR EACH ROW EXECUTE FUNCTION "public"."dorna_auditoria_evento"();


--
-- Name: albaranes_cliente albaranes_cliente_id_factura_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."albaranes_cliente"
    ADD CONSTRAINT "albaranes_cliente_id_factura_fkey" FOREIGN KEY ("id_factura") REFERENCES "public"."facturas_cliente"("id_factura") ON DELETE RESTRICT;


--
-- Name: albaranes_proveedor albaranes_proveedor_id_factura_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."albaranes_proveedor"
    ADD CONSTRAINT "albaranes_proveedor_id_factura_fkey" FOREIGN KEY ("id_factura") REFERENCES "public"."facturas_proveedor"("id_factura") ON DELETE RESTRICT;


--
-- Name: api_accesos_empresa api_accesos_empresa_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_accesos_empresa"
    ADD CONSTRAINT "api_accesos_empresa_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_accesos_empresa api_accesos_empresa_id_rol_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_accesos_empresa"
    ADD CONSTRAINT "api_accesos_empresa_id_rol_fkey" FOREIGN KEY ("id_rol") REFERENCES "public"."roles"("id_rol");


--
-- Name: api_accesos_empresa api_accesos_empresa_id_usuario_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_accesos_empresa"
    ADD CONSTRAINT "api_accesos_empresa_id_usuario_fkey" FOREIGN KEY ("id_usuario") REFERENCES "public"."usuarios"("id_usuario");


--
-- Name: api_albaran_cliente_empresa api_albaran_cliente_empresa_id_albaran_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_albaran_cliente_empresa"
    ADD CONSTRAINT "api_albaran_cliente_empresa_id_albaran_fkey" FOREIGN KEY ("id_albaran") REFERENCES "public"."albaranes_cliente"("id_albaran");


--
-- Name: api_albaran_cliente_empresa api_albaran_cliente_empresa_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_albaran_cliente_empresa"
    ADD CONSTRAINT "api_albaran_cliente_empresa_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_albaran_proveedor_empresa api_albaran_proveedor_empresa_id_albaran_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_albaran_proveedor_empresa"
    ADD CONSTRAINT "api_albaran_proveedor_empresa_id_albaran_fkey" FOREIGN KEY ("id_albaran") REFERENCES "public"."albaranes_proveedor"("id_albaran");


--
-- Name: api_albaran_proveedor_empresa api_albaran_proveedor_empresa_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_albaran_proveedor_empresa"
    ADD CONSTRAINT "api_albaran_proveedor_empresa_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_almacen_empresa api_almacen_empresa_id_almacen_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_almacen_empresa"
    ADD CONSTRAINT "api_almacen_empresa_id_almacen_fkey" FOREIGN KEY ("id_almacen") REFERENCES "public"."almacenes"("id_almacen");


--
-- Name: api_almacen_empresa api_almacen_empresa_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_almacen_empresa"
    ADD CONSTRAINT "api_almacen_empresa_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_archivo_documento api_archivo_documento_id_documento_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_archivo_documento"
    ADD CONSTRAINT "api_archivo_documento_id_documento_fkey" FOREIGN KEY ("id_documento") REFERENCES "public"."documentos"("id_documento");


--
-- Name: api_archivo_documento api_archivo_documento_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_archivo_documento"
    ADD CONSTRAINT "api_archivo_documento_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_articulo_empresa api_articulo_empresa_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_articulo_empresa"
    ADD CONSTRAINT "api_articulo_empresa_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_articulo_empresa api_articulo_empresa_referencia_articulo_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_articulo_empresa"
    ADD CONSTRAINT "api_articulo_empresa_referencia_articulo_fkey" FOREIGN KEY ("referencia_articulo") REFERENCES "public"."articulos"("referencia");


--
-- Name: api_asignaciones_obra api_asignaciones_obra_id_obra_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_asignaciones_obra"
    ADD CONSTRAINT "api_asignaciones_obra_id_obra_id_empresa_fkey" FOREIGN KEY ("id_obra", "id_empresa") REFERENCES "public"."api_obra_empresa"("id_obra", "id_empresa");


--
-- Name: api_asignaciones_obra api_asignaciones_obra_id_operario_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_asignaciones_obra"
    ADD CONSTRAINT "api_asignaciones_obra_id_operario_id_empresa_fkey" FOREIGN KEY ("id_operario", "id_empresa") REFERENCES "public"."api_operario_empresa"("id_operario", "id_empresa");


--
-- Name: api_ausencias api_ausencias_id_operario_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_ausencias"
    ADD CONSTRAINT "api_ausencias_id_operario_id_empresa_fkey" FOREIGN KEY ("id_operario", "id_empresa") REFERENCES "public"."api_operario_empresa"("id_operario", "id_empresa");


--
-- Name: api_cliente_empresa api_cliente_empresa_id_cliente_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_cliente_empresa"
    ADD CONSTRAINT "api_cliente_empresa_id_cliente_fkey" FOREIGN KEY ("id_cliente") REFERENCES "public"."clientes"("id_cliente");


--
-- Name: api_cliente_empresa api_cliente_empresa_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_cliente_empresa"
    ADD CONSTRAINT "api_cliente_empresa_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_documentos_auditoria api_documentos_auditoria_autor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_documentos_auditoria"
    ADD CONSTRAINT "api_documentos_auditoria_autor_fkey" FOREIGN KEY ("autor") REFERENCES "public"."usuarios"("id_usuario");


--
-- Name: api_documentos_auditoria api_documentos_auditoria_documento_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_documentos_auditoria"
    ADD CONSTRAINT "api_documentos_auditoria_documento_fkey" FOREIGN KEY ("documento") REFERENCES "public"."api_documentos"("id");


--
-- Name: api_documentos api_documentos_ausencia_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_documentos"
    ADD CONSTRAINT "api_documentos_ausencia_id_empresa_fkey" FOREIGN KEY ("ausencia", "id_empresa") REFERENCES "public"."api_ausencias"("id", "id_empresa");


--
-- Name: api_documentos api_documentos_autor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_documentos"
    ADD CONSTRAINT "api_documentos_autor_fkey" FOREIGN KEY ("autor") REFERENCES "public"."usuarios"("id_usuario");


--
-- Name: api_documentos api_documentos_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_documentos"
    ADD CONSTRAINT "api_documentos_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_documentos api_documentos_obra_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_documentos"
    ADD CONSTRAINT "api_documentos_obra_id_empresa_fkey" FOREIGN KEY ("obra", "id_empresa") REFERENCES "public"."api_obra_empresa"("id_obra", "id_empresa");


--
-- Name: api_documentos api_documentos_presupuesto_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_documentos"
    ADD CONSTRAINT "api_documentos_presupuesto_id_empresa_fkey" FOREIGN KEY ("presupuesto", "id_empresa") REFERENCES "public"."api_presupuesto_empresa"("id_presupuesto", "id_empresa");


--
-- Name: api_empresas api_empresas_id_entidad_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_empresas"
    ADD CONSTRAINT "api_empresas_id_entidad_fkey" FOREIGN KEY ("id_entidad") REFERENCES "public"."entidades"("id_entidad");


--
-- Name: api_evento_empresa api_evento_empresa_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_evento_empresa"
    ADD CONSTRAINT "api_evento_empresa_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_evento_empresa api_evento_empresa_id_evento_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_evento_empresa"
    ADD CONSTRAINT "api_evento_empresa_id_evento_fkey" FOREIGN KEY ("id_evento") REFERENCES "public"."eventos"("id_evento");


--
-- Name: api_factura_cliente_empresa api_factura_cliente_empresa_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_factura_cliente_empresa"
    ADD CONSTRAINT "api_factura_cliente_empresa_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_factura_cliente_empresa api_factura_cliente_empresa_id_factura_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_factura_cliente_empresa"
    ADD CONSTRAINT "api_factura_cliente_empresa_id_factura_fkey" FOREIGN KEY ("id_factura") REFERENCES "public"."facturas_cliente"("id_factura");


--
-- Name: api_factura_proveedor_empresa api_factura_proveedor_empresa_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_factura_proveedor_empresa"
    ADD CONSTRAINT "api_factura_proveedor_empresa_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_factura_proveedor_empresa api_factura_proveedor_empresa_id_factura_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_factura_proveedor_empresa"
    ADD CONSTRAINT "api_factura_proveedor_empresa_id_factura_fkey" FOREIGN KEY ("id_factura") REFERENCES "public"."facturas_proveedor"("id_factura");


--
-- Name: api_facturacion_auditoria api_facturacion_auditoria_autor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion_auditoria"
    ADD CONSTRAINT "api_facturacion_auditoria_autor_fkey" FOREIGN KEY ("autor") REFERENCES "public"."usuarios"("id_usuario");


--
-- Name: api_facturacion_auditoria api_facturacion_auditoria_documento_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion_auditoria"
    ADD CONSTRAINT "api_facturacion_auditoria_documento_fkey" FOREIGN KEY ("documento") REFERENCES "public"."api_facturacion"("id");


--
-- Name: api_facturacion_auditoria api_facturacion_auditoria_serie_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion_auditoria"
    ADD CONSTRAINT "api_facturacion_auditoria_serie_fkey" FOREIGN KEY ("serie") REFERENCES "public"."series_documentos"("id_serie");


--
-- Name: api_facturacion api_facturacion_autor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion"
    ADD CONSTRAINT "api_facturacion_autor_fkey" FOREIGN KEY ("autor") REFERENCES "public"."usuarios"("id_usuario");


--
-- Name: api_facturacion api_facturacion_cliente_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion"
    ADD CONSTRAINT "api_facturacion_cliente_fkey" FOREIGN KEY ("cliente") REFERENCES "public"."clientes"("id_cliente");


--
-- Name: api_facturacion api_facturacion_cliente_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion"
    ADD CONSTRAINT "api_facturacion_cliente_id_empresa_fkey" FOREIGN KEY ("cliente", "id_empresa") REFERENCES "public"."api_cliente_empresa"("id_cliente", "id_empresa");


--
-- Name: api_facturacion api_facturacion_factura_cliente_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion"
    ADD CONSTRAINT "api_facturacion_factura_cliente_fkey" FOREIGN KEY ("factura_cliente") REFERENCES "public"."facturas_cliente"("id_factura");


--
-- Name: api_facturacion api_facturacion_factura_proveedor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion"
    ADD CONSTRAINT "api_facturacion_factura_proveedor_fkey" FOREIGN KEY ("factura_proveedor") REFERENCES "public"."facturas_proveedor"("id_factura");


--
-- Name: api_facturacion api_facturacion_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion"
    ADD CONSTRAINT "api_facturacion_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_facturacion api_facturacion_id_serie_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion"
    ADD CONSTRAINT "api_facturacion_id_serie_id_empresa_fkey" FOREIGN KEY ("id_serie", "id_empresa") REFERENCES "public"."series_documentos"("id_serie", "id_empresa");


--
-- Name: api_facturacion_lineas api_facturacion_lineas_documento_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion_lineas"
    ADD CONSTRAINT "api_facturacion_lineas_documento_fkey" FOREIGN KEY ("documento") REFERENCES "public"."api_facturacion"("id");


--
-- Name: api_facturacion api_facturacion_obra_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion"
    ADD CONSTRAINT "api_facturacion_obra_id_empresa_fkey" FOREIGN KEY ("obra", "id_empresa") REFERENCES "public"."api_obra_empresa"("id_obra", "id_empresa");


--
-- Name: api_facturacion api_facturacion_original_id_empresa_tipo_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion"
    ADD CONSTRAINT "api_facturacion_original_id_empresa_tipo_fkey" FOREIGN KEY ("original", "id_empresa", "tipo") REFERENCES "public"."api_facturacion"("id", "id_empresa", "tipo");


--
-- Name: api_facturacion api_facturacion_proveedor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion"
    ADD CONSTRAINT "api_facturacion_proveedor_fkey" FOREIGN KEY ("proveedor") REFERENCES "public"."proveedores"("id_proveedor");


--
-- Name: api_facturacion api_facturacion_proveedor_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_facturacion"
    ADD CONSTRAINT "api_facturacion_proveedor_id_empresa_fkey" FOREIGN KEY ("proveedor", "id_empresa") REFERENCES "public"."api_proveedor_empresa"("id_proveedor", "id_empresa");


--
-- Name: api_gestion_auditoria api_gestion_auditoria_autor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_gestion_auditoria"
    ADD CONSTRAINT "api_gestion_auditoria_autor_fkey" FOREIGN KEY ("autor") REFERENCES "public"."usuarios"("id_usuario");


--
-- Name: api_gestion_auditoria api_gestion_auditoria_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_gestion_auditoria"
    ADD CONSTRAINT "api_gestion_auditoria_empresa_fkey" FOREIGN KEY ("empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_incidencia_empresa api_incidencia_empresa_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_incidencia_empresa"
    ADD CONSTRAINT "api_incidencia_empresa_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_incidencia_empresa api_incidencia_empresa_id_incidencia_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_incidencia_empresa"
    ADD CONSTRAINT "api_incidencia_empresa_id_incidencia_fkey" FOREIGN KEY ("id_incidencia") REFERENCES "public"."incidencias"("id_incidencia");


--
-- Name: api_laboral_auditoria api_laboral_auditoria_autor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_laboral_auditoria"
    ADD CONSTRAINT "api_laboral_auditoria_autor_fkey" FOREIGN KEY ("autor") REFERENCES "public"."usuarios"("id_usuario");


--
-- Name: api_laboral_auditoria api_laboral_auditoria_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_laboral_auditoria"
    ADD CONSTRAINT "api_laboral_auditoria_empresa_fkey" FOREIGN KEY ("empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_laboral_auditoria api_laboral_auditoria_operario_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_laboral_auditoria"
    ADD CONSTRAINT "api_laboral_auditoria_operario_fkey" FOREIGN KEY ("operario") REFERENCES "public"."operarios"("id_operario");


--
-- Name: api_obra_empresa api_obra_empresa_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_obra_empresa"
    ADD CONSTRAINT "api_obra_empresa_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_obra_empresa api_obra_empresa_id_obra_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_obra_empresa"
    ADD CONSTRAINT "api_obra_empresa_id_obra_fkey" FOREIGN KEY ("id_obra") REFERENCES "public"."obras"("id_obra");


--
-- Name: api_observaciones_auditoria api_observaciones_auditoria_autor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_observaciones_auditoria"
    ADD CONSTRAINT "api_observaciones_auditoria_autor_fkey" FOREIGN KEY ("autor") REFERENCES "public"."usuarios"("id_usuario");


--
-- Name: api_observaciones_auditoria api_observaciones_auditoria_observacion_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_observaciones_auditoria"
    ADD CONSTRAINT "api_observaciones_auditoria_observacion_fkey" FOREIGN KEY ("observacion") REFERENCES "public"."api_observaciones_obra"("id");


--
-- Name: api_observaciones_obra api_observaciones_obra_autor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_observaciones_obra"
    ADD CONSTRAINT "api_observaciones_obra_autor_fkey" FOREIGN KEY ("autor") REFERENCES "public"."usuarios"("id_usuario");


--
-- Name: api_observaciones_obra api_observaciones_obra_id_obra_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_observaciones_obra"
    ADD CONSTRAINT "api_observaciones_obra_id_obra_id_empresa_fkey" FOREIGN KEY ("id_obra", "id_empresa") REFERENCES "public"."api_obra_empresa"("id_obra", "id_empresa");


--
-- Name: api_observaciones_obra api_observaciones_obra_resuelto_por_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_observaciones_obra"
    ADD CONSTRAINT "api_observaciones_obra_resuelto_por_fkey" FOREIGN KEY ("resuelto_por") REFERENCES "public"."usuarios"("id_usuario");


--
-- Name: api_operario_empresa api_operario_empresa_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_operario_empresa"
    ADD CONSTRAINT "api_operario_empresa_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_operario_empresa api_operario_empresa_id_operario_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_operario_empresa"
    ADD CONSTRAINT "api_operario_empresa_id_operario_fkey" FOREIGN KEY ("id_operario") REFERENCES "public"."operarios"("id_operario");


--
-- Name: api_pedido_proveedor_empresa api_pedido_proveedor_empresa_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_pedido_proveedor_empresa"
    ADD CONSTRAINT "api_pedido_proveedor_empresa_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_pedido_proveedor_empresa api_pedido_proveedor_empresa_id_pedido_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_pedido_proveedor_empresa"
    ADD CONSTRAINT "api_pedido_proveedor_empresa_id_pedido_fkey" FOREIGN KEY ("id_pedido") REFERENCES "public"."pedidos_proveedor"("id_pedido");


--
-- Name: api_presupuesto_empresa api_presupuesto_empresa_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_presupuesto_empresa"
    ADD CONSTRAINT "api_presupuesto_empresa_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_presupuesto_empresa api_presupuesto_empresa_id_presupuesto_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_presupuesto_empresa"
    ADD CONSTRAINT "api_presupuesto_empresa_id_presupuesto_fkey" FOREIGN KEY ("id_presupuesto") REFERENCES "public"."presupuestos"("id_presupuesto");


--
-- Name: api_proveedor_empresa api_proveedor_empresa_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_proveedor_empresa"
    ADD CONSTRAINT "api_proveedor_empresa_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_proveedor_empresa api_proveedor_empresa_id_proveedor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_proveedor_empresa"
    ADD CONSTRAINT "api_proveedor_empresa_id_proveedor_fkey" FOREIGN KEY ("id_proveedor") REFERENCES "public"."proveedores"("id_proveedor");


--
-- Name: api_roles_globales api_roles_globales_id_rol_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_roles_globales"
    ADD CONSTRAINT "api_roles_globales_id_rol_fkey" FOREIGN KEY ("id_rol") REFERENCES "public"."roles"("id_rol");


--
-- Name: api_sesiones api_sesiones_id_usuario_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_sesiones"
    ADD CONSTRAINT "api_sesiones_id_usuario_fkey" FOREIGN KEY ("id_usuario") REFERENCES "public"."usuarios"("id_usuario");


--
-- Name: api_tarea_empresa api_tarea_empresa_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tarea_empresa"
    ADD CONSTRAINT "api_tarea_empresa_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_tarea_empresa api_tarea_empresa_id_tarea_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tarea_empresa"
    ADD CONSTRAINT "api_tarea_empresa_id_tarea_fkey" FOREIGN KEY ("id_tarea") REFERENCES "public"."tareas"("id_tarea");


--
-- Name: api_tesoreria_aplicaciones api_tesoreria_aplicaciones_cobro_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_aplicaciones"
    ADD CONSTRAINT "api_tesoreria_aplicaciones_cobro_fkey" FOREIGN KEY ("cobro") REFERENCES "public"."cobros"("id_cobro");


--
-- Name: api_tesoreria_aplicaciones api_tesoreria_aplicaciones_cobro_reversion_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_aplicaciones"
    ADD CONSTRAINT "api_tesoreria_aplicaciones_cobro_reversion_fkey" FOREIGN KEY ("cobro_reversion") REFERENCES "public"."cobros"("id_cobro");


--
-- Name: api_tesoreria_aplicaciones api_tesoreria_aplicaciones_movimiento_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_aplicaciones"
    ADD CONSTRAINT "api_tesoreria_aplicaciones_movimiento_fkey" FOREIGN KEY ("movimiento") REFERENCES "public"."api_tesoreria"("id");


--
-- Name: api_tesoreria_aplicaciones api_tesoreria_aplicaciones_pago_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_aplicaciones"
    ADD CONSTRAINT "api_tesoreria_aplicaciones_pago_fkey" FOREIGN KEY ("pago") REFERENCES "public"."pagos"("id_pago");


--
-- Name: api_tesoreria_aplicaciones api_tesoreria_aplicaciones_pago_reversion_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_aplicaciones"
    ADD CONSTRAINT "api_tesoreria_aplicaciones_pago_reversion_fkey" FOREIGN KEY ("pago_reversion") REFERENCES "public"."pagos"("id_pago");


--
-- Name: api_tesoreria_aplicaciones api_tesoreria_aplicaciones_vencimiento_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_aplicaciones"
    ADD CONSTRAINT "api_tesoreria_aplicaciones_vencimiento_fkey" FOREIGN KEY ("vencimiento") REFERENCES "public"."api_vencimientos"("id");


--
-- Name: api_tesoreria_auditoria api_tesoreria_auditoria_autor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_auditoria"
    ADD CONSTRAINT "api_tesoreria_auditoria_autor_fkey" FOREIGN KEY ("autor") REFERENCES "public"."usuarios"("id_usuario");


--
-- Name: api_tesoreria_auditoria api_tesoreria_auditoria_documento_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_auditoria"
    ADD CONSTRAINT "api_tesoreria_auditoria_documento_fkey" FOREIGN KEY ("documento") REFERENCES "public"."api_facturacion"("id");


--
-- Name: api_tesoreria_auditoria api_tesoreria_auditoria_movimiento_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria_auditoria"
    ADD CONSTRAINT "api_tesoreria_auditoria_movimiento_fkey" FOREIGN KEY ("movimiento") REFERENCES "public"."api_tesoreria"("id");


--
-- Name: api_tesoreria api_tesoreria_autor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria"
    ADD CONSTRAINT "api_tesoreria_autor_fkey" FOREIGN KEY ("autor") REFERENCES "public"."usuarios"("id_usuario");


--
-- Name: api_tesoreria api_tesoreria_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_tesoreria"
    ADD CONSTRAINT "api_tesoreria_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_traspaso_stock api_traspaso_stock_id_almacen_destino_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_traspaso_stock"
    ADD CONSTRAINT "api_traspaso_stock_id_almacen_destino_fkey" FOREIGN KEY ("id_almacen_destino") REFERENCES "public"."almacenes"("id_almacen");


--
-- Name: api_traspaso_stock api_traspaso_stock_id_movimiento_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_traspaso_stock"
    ADD CONSTRAINT "api_traspaso_stock_id_movimiento_fkey" FOREIGN KEY ("id_movimiento") REFERENCES "public"."movimientos_stock"("id_movimiento");


--
-- Name: api_usuario_operario api_usuario_operario_id_operario_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_usuario_operario"
    ADD CONSTRAINT "api_usuario_operario_id_operario_fkey" FOREIGN KEY ("id_operario") REFERENCES "public"."operarios"("id_operario");


--
-- Name: api_usuario_operario api_usuario_operario_id_usuario_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_usuario_operario"
    ADD CONSTRAINT "api_usuario_operario_id_usuario_fkey" FOREIGN KEY ("id_usuario") REFERENCES "public"."usuarios"("id_usuario");


--
-- Name: api_usuarios_auditoria api_usuarios_auditoria_autor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_usuarios_auditoria"
    ADD CONSTRAINT "api_usuarios_auditoria_autor_fkey" FOREIGN KEY ("autor") REFERENCES "public"."usuarios"("id_usuario");


--
-- Name: api_usuarios_auditoria api_usuarios_auditoria_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_usuarios_auditoria"
    ADD CONSTRAINT "api_usuarios_auditoria_empresa_fkey" FOREIGN KEY ("empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_usuarios_auditoria api_usuarios_auditoria_rol_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_usuarios_auditoria"
    ADD CONSTRAINT "api_usuarios_auditoria_rol_fkey" FOREIGN KEY ("rol") REFERENCES "public"."roles"("id_rol");


--
-- Name: api_usuarios_auditoria api_usuarios_auditoria_usuario_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_usuarios_auditoria"
    ADD CONSTRAINT "api_usuarios_auditoria_usuario_fkey" FOREIGN KEY ("usuario") REFERENCES "public"."usuarios"("id_usuario");


--
-- Name: api_vencimientos api_vencimientos_documento_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_vencimientos"
    ADD CONSTRAINT "api_vencimientos_documento_fkey" FOREIGN KEY ("documento") REFERENCES "public"."api_facturacion"("id");


--
-- Name: api_visibilidad_auditoria api_visibilidad_auditoria_empresa_destino_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_visibilidad_auditoria"
    ADD CONSTRAINT "api_visibilidad_auditoria_empresa_destino_fkey" FOREIGN KEY ("empresa_destino") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_visibilidad_auditoria api_visibilidad_auditoria_empresa_origen_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_visibilidad_auditoria"
    ADD CONSTRAINT "api_visibilidad_auditoria_empresa_origen_fkey" FOREIGN KEY ("empresa_origen") REFERENCES "public"."api_empresas"("id_empresa");


--
-- Name: api_visibilidad_auditoria api_visibilidad_auditoria_id_cliente_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_visibilidad_auditoria"
    ADD CONSTRAINT "api_visibilidad_auditoria_id_cliente_fkey" FOREIGN KEY ("id_cliente") REFERENCES "public"."clientes"("id_cliente");


--
-- Name: api_visibilidad_auditoria api_visibilidad_auditoria_id_proveedor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_visibilidad_auditoria"
    ADD CONSTRAINT "api_visibilidad_auditoria_id_proveedor_fkey" FOREIGN KEY ("id_proveedor") REFERENCES "public"."proveedores"("id_proveedor");


--
-- Name: api_visibilidad_auditoria api_visibilidad_auditoria_id_usuario_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."api_visibilidad_auditoria"
    ADD CONSTRAINT "api_visibilidad_auditoria_id_usuario_fkey" FOREIGN KEY ("id_usuario") REFERENCES "public"."usuarios"("id_usuario");


--
-- Name: albaranes_cliente fk_albaran_cliente_cliente; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."albaranes_cliente"
    ADD CONSTRAINT "fk_albaran_cliente_cliente" FOREIGN KEY ("id_cliente") REFERENCES "public"."clientes"("id_cliente") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: albaranes_cliente fk_albaran_cliente_estado; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."albaranes_cliente"
    ADD CONSTRAINT "fk_albaran_cliente_estado" FOREIGN KEY ("id_estado") REFERENCES "public"."estados_factura"("id_estado") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: albaranes_cliente fk_albaran_cliente_serie; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."albaranes_cliente"
    ADD CONSTRAINT "fk_albaran_cliente_serie" FOREIGN KEY ("id_serie") REFERENCES "public"."series_documentos"("id_serie") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: albaranes_cliente fk_albaran_cliente_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."albaranes_cliente"
    ADD CONSTRAINT "fk_albaran_cliente_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: albaranes_cliente fk_albaran_cliente_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."albaranes_cliente"
    ADD CONSTRAINT "fk_albaran_cliente_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: albaranes_proveedor fk_albaran_proveedor_estado; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."albaranes_proveedor"
    ADD CONSTRAINT "fk_albaran_proveedor_estado" FOREIGN KEY ("id_estado") REFERENCES "public"."estados_factura"("id_estado") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: albaranes_proveedor fk_albaran_proveedor_proveedor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."albaranes_proveedor"
    ADD CONSTRAINT "fk_albaran_proveedor_proveedor" FOREIGN KEY ("id_proveedor") REFERENCES "public"."proveedores"("id_proveedor") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: albaranes_proveedor fk_albaran_proveedor_serie; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."albaranes_proveedor"
    ADD CONSTRAINT "fk_albaran_proveedor_serie" FOREIGN KEY ("id_serie") REFERENCES "public"."series_documentos"("id_serie") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: albaranes_proveedor fk_albaran_proveedor_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."albaranes_proveedor"
    ADD CONSTRAINT "fk_albaran_proveedor_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: albaranes_proveedor fk_albaran_proveedor_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."albaranes_proveedor"
    ADD CONSTRAINT "fk_albaran_proveedor_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: almacenes fk_almacen_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."almacenes"
    ADD CONSTRAINT "fk_almacen_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: almacenes fk_almacen_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."almacenes"
    ADD CONSTRAINT "fk_almacen_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: articulos fk_articulo_familia; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."articulos"
    ADD CONSTRAINT "fk_articulo_familia" FOREIGN KEY ("id_familia") REFERENCES "public"."familias"("id_familia") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: articulos fk_articulo_iva; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."articulos"
    ADD CONSTRAINT "fk_articulo_iva" FOREIGN KEY ("id_iva") REFERENCES "public"."tipos_iva"("id_iva") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: articulos fk_articulo_proveedor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."articulos"
    ADD CONSTRAINT "fk_articulo_proveedor" FOREIGN KEY ("id_proveedor") REFERENCES "public"."proveedores"("id_proveedor") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: articulos fk_articulo_unidad; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."articulos"
    ADD CONSTRAINT "fk_articulo_unidad" FOREIGN KEY ("id_unidad") REFERENCES "public"."unidades_medida"("id_unidad") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: articulos fk_articulo_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."articulos"
    ADD CONSTRAINT "fk_articulo_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: articulos fk_articulo_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."articulos"
    ADD CONSTRAINT "fk_articulo_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: categorias_operarios fk_categoria_operario_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."categorias_operarios"
    ADD CONSTRAINT "fk_categoria_operario_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: categorias_operarios fk_categoria_operario_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."categorias_operarios"
    ADD CONSTRAINT "fk_categoria_operario_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: clientes fk_cliente_entidad; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."clientes"
    ADD CONSTRAINT "fk_cliente_entidad" FOREIGN KEY ("id_entidad") REFERENCES "public"."entidades"("id_entidad") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: clientes fk_cliente_forma_pago; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."clientes"
    ADD CONSTRAINT "fk_cliente_forma_pago" FOREIGN KEY ("id_forma_pago") REFERENCES "public"."formas_pago"("id_forma_pago") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: clientes fk_cliente_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."clientes"
    ADD CONSTRAINT "fk_cliente_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: clientes fk_cliente_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."clientes"
    ADD CONSTRAINT "fk_cliente_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: clientes fk_cliente_zona; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."clientes"
    ADD CONSTRAINT "fk_cliente_zona" FOREIGN KEY ("id_zona") REFERENCES "public"."zonas"("id_zona") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: cobros fk_cobro_factura; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."cobros"
    ADD CONSTRAINT "fk_cobro_factura" FOREIGN KEY ("id_factura") REFERENCES "public"."facturas_cliente"("id_factura");


--
-- Name: cobros fk_cobro_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."cobros"
    ADD CONSTRAINT "fk_cobro_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: cobros fk_cobro_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."cobros"
    ADD CONSTRAINT "fk_cobro_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: contactos fk_contacto_entidad; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."contactos"
    ADD CONSTRAINT "fk_contacto_entidad" FOREIGN KEY ("id_entidad") REFERENCES "public"."entidades"("id_entidad") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: contactos fk_contacto_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."contactos"
    ADD CONSTRAINT "fk_contacto_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: contactos fk_contacto_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."contactos"
    ADD CONSTRAINT "fk_contacto_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: direcciones fk_direccion_entidad; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."direcciones"
    ADD CONSTRAINT "fk_direccion_entidad" FOREIGN KEY ("id_entidad") REFERENCES "public"."entidades"("id_entidad") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: direcciones fk_direccion_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."direcciones"
    ADD CONSTRAINT "fk_direccion_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: direcciones fk_direccion_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."direcciones"
    ADD CONSTRAINT "fk_direccion_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: documentos_generales fk_doc_general_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."documentos_generales"
    ADD CONSTRAINT "fk_doc_general_usuario" FOREIGN KEY ("id_usuario") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: documentos_generales fk_doc_general_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."documentos_generales"
    ADD CONSTRAINT "fk_doc_general_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: documentos_generales fk_doc_general_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."documentos_generales"
    ADD CONSTRAINT "fk_doc_general_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: documentos fk_documento_obra; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."documentos"
    ADD CONSTRAINT "fk_documento_obra" FOREIGN KEY ("id_obra") REFERENCES "public"."obras"("id_obra") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: documentos fk_documento_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."documentos"
    ADD CONSTRAINT "fk_documento_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: documentos fk_documento_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."documentos"
    ADD CONSTRAINT "fk_documento_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: entidades fk_entidad_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."entidades"
    ADD CONSTRAINT "fk_entidad_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: entidades fk_entidad_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."entidades"
    ADD CONSTRAINT "fk_entidad_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: estados_factura fk_estado_factura_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."estados_factura"
    ADD CONSTRAINT "fk_estado_factura_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: estados_factura fk_estado_factura_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."estados_factura"
    ADD CONSTRAINT "fk_estado_factura_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: estados_incidencia fk_estado_incidencia_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."estados_incidencia"
    ADD CONSTRAINT "fk_estado_incidencia_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: estados_incidencia fk_estado_incidencia_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."estados_incidencia"
    ADD CONSTRAINT "fk_estado_incidencia_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: estados_obra fk_estado_obra_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."estados_obra"
    ADD CONSTRAINT "fk_estado_obra_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: estados_obra fk_estado_obra_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."estados_obra"
    ADD CONSTRAINT "fk_estado_obra_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: estados_tarea fk_estado_tarea_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."estados_tarea"
    ADD CONSTRAINT "fk_estado_tarea_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: estados_tarea fk_estado_tarea_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."estados_tarea"
    ADD CONSTRAINT "fk_estado_tarea_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: eventos fk_evento_obra; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."eventos"
    ADD CONSTRAINT "fk_evento_obra" FOREIGN KEY ("id_obra") REFERENCES "public"."obras"("id_obra") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: eventos fk_evento_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."eventos"
    ADD CONSTRAINT "fk_evento_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: eventos fk_evento_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."eventos"
    ADD CONSTRAINT "fk_evento_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: familias fk_familia_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."familias"
    ADD CONSTRAINT "fk_familia_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: familias fk_familia_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."familias"
    ADD CONSTRAINT "fk_familia_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: facturas_cliente fk_fc_cliente; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."facturas_cliente"
    ADD CONSTRAINT "fk_fc_cliente" FOREIGN KEY ("id_cliente") REFERENCES "public"."clientes"("id_cliente") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: facturas_cliente fk_fc_estado; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."facturas_cliente"
    ADD CONSTRAINT "fk_fc_estado" FOREIGN KEY ("id_estado") REFERENCES "public"."estados_factura"("id_estado") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: facturas_cliente fk_fc_obra; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."facturas_cliente"
    ADD CONSTRAINT "fk_fc_obra" FOREIGN KEY ("id_obra") REFERENCES "public"."obras"("id_obra") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: facturas_cliente fk_fc_serie; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."facturas_cliente"
    ADD CONSTRAINT "fk_fc_serie" FOREIGN KEY ("id_serie") REFERENCES "public"."series_documentos"("id_serie") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: facturas_cliente fk_fc_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."facturas_cliente"
    ADD CONSTRAINT "fk_fc_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: facturas_cliente fk_fc_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."facturas_cliente"
    ADD CONSTRAINT "fk_fc_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: formas_pago fk_forma_pago_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."formas_pago"
    ADD CONSTRAINT "fk_forma_pago_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: formas_pago fk_forma_pago_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."formas_pago"
    ADD CONSTRAINT "fk_forma_pago_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: facturas_proveedor fk_fp_estado; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."facturas_proveedor"
    ADD CONSTRAINT "fk_fp_estado" FOREIGN KEY ("id_estado") REFERENCES "public"."estados_factura"("id_estado") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: facturas_proveedor fk_fp_proveedor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."facturas_proveedor"
    ADD CONSTRAINT "fk_fp_proveedor" FOREIGN KEY ("id_proveedor") REFERENCES "public"."proveedores"("id_proveedor") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: facturas_proveedor fk_fp_serie; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."facturas_proveedor"
    ADD CONSTRAINT "fk_fp_serie" FOREIGN KEY ("id_serie") REFERENCES "public"."series_documentos"("id_serie") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: facturas_proveedor fk_fp_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."facturas_proveedor"
    ADD CONSTRAINT "fk_fp_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: facturas_proveedor fk_fp_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."facturas_proveedor"
    ADD CONSTRAINT "fk_fp_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: historial_estados_obra fk_historial_estado_anterior; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."historial_estados_obra"
    ADD CONSTRAINT "fk_historial_estado_anterior" FOREIGN KEY ("id_estado_anterior") REFERENCES "public"."estados_obra"("id_estado") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: historial_estados_obra fk_historial_estado_nuevo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."historial_estados_obra"
    ADD CONSTRAINT "fk_historial_estado_nuevo" FOREIGN KEY ("id_estado_nuevo") REFERENCES "public"."estados_obra"("id_estado") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: historial_estados_obra fk_historial_obra; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."historial_estados_obra"
    ADD CONSTRAINT "fk_historial_obra" FOREIGN KEY ("id_obra") REFERENCES "public"."obras"("id_obra") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: historial_estados_obra fk_historial_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."historial_estados_obra"
    ADD CONSTRAINT "fk_historial_usuario" FOREIGN KEY ("id_usuario") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: historial_estados_obra fk_historial_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."historial_estados_obra"
    ADD CONSTRAINT "fk_historial_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: historial_estados_obra fk_historial_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."historial_estados_obra"
    ADD CONSTRAINT "fk_historial_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: incidencias fk_incidencia_estado; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."incidencias"
    ADD CONSTRAINT "fk_incidencia_estado" FOREIGN KEY ("id_estado") REFERENCES "public"."estados_incidencia"("id_estado") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: incidencias fk_incidencia_obra; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."incidencias"
    ADD CONSTRAINT "fk_incidencia_obra" FOREIGN KEY ("id_obra") REFERENCES "public"."obras"("id_obra") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: incidencias fk_incidencia_operario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."incidencias"
    ADD CONSTRAINT "fk_incidencia_operario" FOREIGN KEY ("id_operario") REFERENCES "public"."operarios"("id_operario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: incidencias fk_incidencia_prioridad; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."incidencias"
    ADD CONSTRAINT "fk_incidencia_prioridad" FOREIGN KEY ("id_prioridad") REFERENCES "public"."prioridades"("id_prioridad") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: incidencias fk_incidencia_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."incidencias"
    ADD CONSTRAINT "fk_incidencia_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: incidencias fk_incidencia_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."incidencias"
    ADD CONSTRAINT "fk_incidencia_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: tipos_iva fk_iva_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."tipos_iva"
    ADD CONSTRAINT "fk_iva_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: tipos_iva fk_iva_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."tipos_iva"
    ADD CONSTRAINT "fk_iva_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: lineas_albaran_cliente fk_lac_albaran; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_albaran_cliente"
    ADD CONSTRAINT "fk_lac_albaran" FOREIGN KEY ("id_albaran") REFERENCES "public"."albaranes_cliente"("id_albaran") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: lineas_albaran_cliente fk_lac_articulo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_albaran_cliente"
    ADD CONSTRAINT "fk_lac_articulo" FOREIGN KEY ("referencia_articulo") REFERENCES "public"."articulos"("referencia") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: lineas_albaran_cliente fk_lac_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_albaran_cliente"
    ADD CONSTRAINT "fk_lac_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: lineas_albaran_cliente fk_lac_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_albaran_cliente"
    ADD CONSTRAINT "fk_lac_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: lineas_albaran_proveedor fk_lap_albaran; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_albaran_proveedor"
    ADD CONSTRAINT "fk_lap_albaran" FOREIGN KEY ("id_albaran") REFERENCES "public"."albaranes_proveedor"("id_albaran") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: lineas_albaran_proveedor fk_lap_articulo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_albaran_proveedor"
    ADD CONSTRAINT "fk_lap_articulo" FOREIGN KEY ("referencia_articulo") REFERENCES "public"."articulos"("referencia") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: lineas_albaran_proveedor fk_lap_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_albaran_proveedor"
    ADD CONSTRAINT "fk_lap_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: lineas_albaran_proveedor fk_lap_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_albaran_proveedor"
    ADD CONSTRAINT "fk_lap_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: lineas_factura_cliente fk_lfc_articulo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_factura_cliente"
    ADD CONSTRAINT "fk_lfc_articulo" FOREIGN KEY ("referencia_articulo") REFERENCES "public"."articulos"("referencia") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: lineas_factura_cliente fk_lfc_factura; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_factura_cliente"
    ADD CONSTRAINT "fk_lfc_factura" FOREIGN KEY ("id_factura") REFERENCES "public"."facturas_cliente"("id_factura");


--
-- Name: lineas_factura_cliente fk_lfc_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_factura_cliente"
    ADD CONSTRAINT "fk_lfc_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: lineas_factura_cliente fk_lfc_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_factura_cliente"
    ADD CONSTRAINT "fk_lfc_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: lineas_factura_proveedor fk_lfp_articulo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_factura_proveedor"
    ADD CONSTRAINT "fk_lfp_articulo" FOREIGN KEY ("referencia_articulo") REFERENCES "public"."articulos"("referencia") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: lineas_factura_proveedor fk_lfp_factura; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_factura_proveedor"
    ADD CONSTRAINT "fk_lfp_factura" FOREIGN KEY ("id_factura") REFERENCES "public"."facturas_proveedor"("id_factura");


--
-- Name: lineas_factura_proveedor fk_lfp_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_factura_proveedor"
    ADD CONSTRAINT "fk_lfp_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: lineas_factura_proveedor fk_lfp_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_factura_proveedor"
    ADD CONSTRAINT "fk_lfp_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: lineas_pedido fk_linea_pedido; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_pedido"
    ADD CONSTRAINT "fk_linea_pedido" FOREIGN KEY ("id_pedido") REFERENCES "public"."pedidos_proveedor"("id_pedido") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: lineas_pedido fk_linea_pedido_articulo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_pedido"
    ADD CONSTRAINT "fk_linea_pedido_articulo" FOREIGN KEY ("referencia_articulo") REFERENCES "public"."articulos"("referencia") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: lineas_pedido fk_linea_pedido_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_pedido"
    ADD CONSTRAINT "fk_linea_pedido_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: lineas_pedido fk_linea_pedido_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_pedido"
    ADD CONSTRAINT "fk_linea_pedido_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: log fk_log_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."log"
    ADD CONSTRAINT "fk_log_usuario" FOREIGN KEY ("id_usuario") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: lineas_presupuesto fk_lp_articulo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_presupuesto"
    ADD CONSTRAINT "fk_lp_articulo" FOREIGN KEY ("referencia_articulo") REFERENCES "public"."articulos"("referencia") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: lineas_presupuesto fk_lp_presupuesto; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_presupuesto"
    ADD CONSTRAINT "fk_lp_presupuesto" FOREIGN KEY ("id_presupuesto") REFERENCES "public"."presupuestos"("id_presupuesto") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: lineas_presupuesto fk_lp_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_presupuesto"
    ADD CONSTRAINT "fk_lp_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: lineas_presupuesto fk_lp_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."lineas_presupuesto"
    ADD CONSTRAINT "fk_lp_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: materiales_por_obra fk_material_almacen; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."materiales_por_obra"
    ADD CONSTRAINT "fk_material_almacen" FOREIGN KEY ("id_almacen") REFERENCES "public"."almacenes"("id_almacen") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: materiales_por_obra fk_material_articulo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."materiales_por_obra"
    ADD CONSTRAINT "fk_material_articulo" FOREIGN KEY ("referencia_articulo") REFERENCES "public"."articulos"("referencia") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: materiales_por_obra fk_material_obra; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."materiales_por_obra"
    ADD CONSTRAINT "fk_material_obra" FOREIGN KEY ("id_obra") REFERENCES "public"."obras"("id_obra") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: materiales_por_obra fk_material_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."materiales_por_obra"
    ADD CONSTRAINT "fk_material_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: materiales_por_obra fk_material_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."materiales_por_obra"
    ADD CONSTRAINT "fk_material_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: obras fk_obra_cliente; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."obras"
    ADD CONSTRAINT "fk_obra_cliente" FOREIGN KEY ("id_cliente") REFERENCES "public"."clientes"("id_cliente") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: obra_contacto fk_obra_contacto_contacto; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."obra_contacto"
    ADD CONSTRAINT "fk_obra_contacto_contacto" FOREIGN KEY ("id_contacto") REFERENCES "public"."contactos"("id_contacto") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: obra_contacto fk_obra_contacto_obra; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."obra_contacto"
    ADD CONSTRAINT "fk_obra_contacto_obra" FOREIGN KEY ("id_obra") REFERENCES "public"."obras"("id_obra") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: obra_contacto fk_obra_contacto_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."obra_contacto"
    ADD CONSTRAINT "fk_obra_contacto_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: obra_contacto fk_obra_contacto_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."obra_contacto"
    ADD CONSTRAINT "fk_obra_contacto_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: obras fk_obra_direccion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."obras"
    ADD CONSTRAINT "fk_obra_direccion" FOREIGN KEY ("id_direccion") REFERENCES "public"."direcciones"("id_direccion") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: obras fk_obra_estado; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."obras"
    ADD CONSTRAINT "fk_obra_estado" FOREIGN KEY ("id_estado") REFERENCES "public"."estados_obra"("id_estado") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: obras fk_obra_serie; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."obras"
    ADD CONSTRAINT "fk_obra_serie" FOREIGN KEY ("id_serie") REFERENCES "public"."series_documentos"("id_serie") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: obras fk_obra_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."obras"
    ADD CONSTRAINT "fk_obra_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: obras fk_obra_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."obras"
    ADD CONSTRAINT "fk_obra_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: operarios fk_operario_categoria; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."operarios"
    ADD CONSTRAINT "fk_operario_categoria" FOREIGN KEY ("id_categoria") REFERENCES "public"."categorias_operarios"("id_categoria") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: operarios fk_operario_entidad; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."operarios"
    ADD CONSTRAINT "fk_operario_entidad" FOREIGN KEY ("id_entidad") REFERENCES "public"."entidades"("id_entidad") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: operarios fk_operario_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."operarios"
    ADD CONSTRAINT "fk_operario_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: operarios fk_operario_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."operarios"
    ADD CONSTRAINT "fk_operario_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: pagos fk_pago_factura; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."pagos"
    ADD CONSTRAINT "fk_pago_factura" FOREIGN KEY ("id_factura") REFERENCES "public"."facturas_proveedor"("id_factura");


--
-- Name: pagos fk_pago_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."pagos"
    ADD CONSTRAINT "fk_pago_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: pagos fk_pago_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."pagos"
    ADD CONSTRAINT "fk_pago_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: partes_mano_obra fk_parte_obra; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."partes_mano_obra"
    ADD CONSTRAINT "fk_parte_obra" FOREIGN KEY ("id_obra") REFERENCES "public"."obras"("id_obra") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: partes_mano_obra fk_parte_operario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."partes_mano_obra"
    ADD CONSTRAINT "fk_parte_operario" FOREIGN KEY ("id_operario") REFERENCES "public"."operarios"("id_operario") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: partes_mano_obra fk_parte_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."partes_mano_obra"
    ADD CONSTRAINT "fk_parte_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: partes_mano_obra fk_parte_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."partes_mano_obra"
    ADD CONSTRAINT "fk_parte_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: pedidos_proveedor fk_pedido_estado; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."pedidos_proveedor"
    ADD CONSTRAINT "fk_pedido_estado" FOREIGN KEY ("id_estado") REFERENCES "public"."estados_factura"("id_estado") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: pedidos_proveedor fk_pedido_proveedor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."pedidos_proveedor"
    ADD CONSTRAINT "fk_pedido_proveedor" FOREIGN KEY ("id_proveedor") REFERENCES "public"."proveedores"("id_proveedor") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: pedidos_proveedor fk_pedido_serie; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."pedidos_proveedor"
    ADD CONSTRAINT "fk_pedido_serie" FOREIGN KEY ("id_serie") REFERENCES "public"."series_documentos"("id_serie") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: pedidos_proveedor fk_pedido_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."pedidos_proveedor"
    ADD CONSTRAINT "fk_pedido_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: pedidos_proveedor fk_pedido_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."pedidos_proveedor"
    ADD CONSTRAINT "fk_pedido_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: permisos fk_permiso_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."permisos"
    ADD CONSTRAINT "fk_permiso_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: permisos fk_permiso_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."permisos"
    ADD CONSTRAINT "fk_permiso_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: presupuestos fk_presupuesto_cliente; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."presupuestos"
    ADD CONSTRAINT "fk_presupuesto_cliente" FOREIGN KEY ("id_cliente") REFERENCES "public"."clientes"("id_cliente") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: presupuestos fk_presupuesto_estado; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."presupuestos"
    ADD CONSTRAINT "fk_presupuesto_estado" FOREIGN KEY ("id_estado") REFERENCES "public"."estados_factura"("id_estado") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: presupuestos fk_presupuesto_obra; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."presupuestos"
    ADD CONSTRAINT "fk_presupuesto_obra" FOREIGN KEY ("id_obra") REFERENCES "public"."obras"("id_obra") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: presupuestos fk_presupuesto_serie; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."presupuestos"
    ADD CONSTRAINT "fk_presupuesto_serie" FOREIGN KEY ("id_serie") REFERENCES "public"."series_documentos"("id_serie") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: presupuestos fk_presupuesto_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."presupuestos"
    ADD CONSTRAINT "fk_presupuesto_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: presupuestos fk_presupuesto_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."presupuestos"
    ADD CONSTRAINT "fk_presupuesto_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: prioridades fk_prioridad_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."prioridades"
    ADD CONSTRAINT "fk_prioridad_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: prioridades fk_prioridad_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."prioridades"
    ADD CONSTRAINT "fk_prioridad_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: proveedores fk_proveedor_entidad; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."proveedores"
    ADD CONSTRAINT "fk_proveedor_entidad" FOREIGN KEY ("id_entidad") REFERENCES "public"."entidades"("id_entidad") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: proveedores fk_proveedor_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."proveedores"
    ADD CONSTRAINT "fk_proveedor_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: proveedores fk_proveedor_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."proveedores"
    ADD CONSTRAINT "fk_proveedor_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: rol_permiso fk_rol_permiso_permiso; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."rol_permiso"
    ADD CONSTRAINT "fk_rol_permiso_permiso" FOREIGN KEY ("id_permiso") REFERENCES "public"."permisos"("id_permiso") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: rol_permiso fk_rol_permiso_rol; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."rol_permiso"
    ADD CONSTRAINT "fk_rol_permiso_rol" FOREIGN KEY ("id_rol") REFERENCES "public"."roles"("id_rol") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: rol_permiso fk_rol_permiso_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."rol_permiso"
    ADD CONSTRAINT "fk_rol_permiso_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: rol_permiso fk_rol_permiso_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."rol_permiso"
    ADD CONSTRAINT "fk_rol_permiso_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: roles fk_rol_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."roles"
    ADD CONSTRAINT "fk_rol_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: roles fk_rol_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."roles"
    ADD CONSTRAINT "fk_rol_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: series_documentos fk_serie_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."series_documentos"
    ADD CONSTRAINT "fk_serie_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: series_documentos fk_serie_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."series_documentos"
    ADD CONSTRAINT "fk_serie_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: movimientos_stock fk_stock_almacen; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."movimientos_stock"
    ADD CONSTRAINT "fk_stock_almacen" FOREIGN KEY ("id_almacen") REFERENCES "public"."almacenes"("id_almacen") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: movimientos_stock fk_stock_articulo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."movimientos_stock"
    ADD CONSTRAINT "fk_stock_articulo" FOREIGN KEY ("referencia_articulo") REFERENCES "public"."articulos"("referencia") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: movimientos_stock fk_stock_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."movimientos_stock"
    ADD CONSTRAINT "fk_stock_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: movimientos_stock fk_stock_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."movimientos_stock"
    ADD CONSTRAINT "fk_stock_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: tareas fk_tarea_estado; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."tareas"
    ADD CONSTRAINT "fk_tarea_estado" FOREIGN KEY ("id_estado") REFERENCES "public"."estados_tarea"("id_estado") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: tareas fk_tarea_obra; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."tareas"
    ADD CONSTRAINT "fk_tarea_obra" FOREIGN KEY ("id_obra") REFERENCES "public"."obras"("id_obra") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: tareas fk_tarea_operario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."tareas"
    ADD CONSTRAINT "fk_tarea_operario" FOREIGN KEY ("id_operario") REFERENCES "public"."operarios"("id_operario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: tareas fk_tarea_prioridad; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."tareas"
    ADD CONSTRAINT "fk_tarea_prioridad" FOREIGN KEY ("id_prioridad") REFERENCES "public"."prioridades"("id_prioridad") ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: tareas fk_tarea_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."tareas"
    ADD CONSTRAINT "fk_tarea_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: tareas fk_tarea_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."tareas"
    ADD CONSTRAINT "fk_tarea_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: unidades_medida fk_unidad_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."unidades_medida"
    ADD CONSTRAINT "fk_unidad_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: unidades_medida fk_unidad_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."unidades_medida"
    ADD CONSTRAINT "fk_unidad_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: usuarios fk_usuario_entidad; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."usuarios"
    ADD CONSTRAINT "fk_usuario_entidad" FOREIGN KEY ("id_entidad") REFERENCES "public"."entidades"("id_entidad") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: usuario_rol fk_usuario_rol_rol; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."usuario_rol"
    ADD CONSTRAINT "fk_usuario_rol_rol" FOREIGN KEY ("id_rol") REFERENCES "public"."roles"("id_rol") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: usuario_rol fk_usuario_rol_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."usuario_rol"
    ADD CONSTRAINT "fk_usuario_rol_usuario" FOREIGN KEY ("id_usuario") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: usuario_rol fk_usuario_rol_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."usuario_rol"
    ADD CONSTRAINT "fk_usuario_rol_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: usuario_rol fk_usuario_rol_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."usuario_rol"
    ADD CONSTRAINT "fk_usuario_rol_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: usuarios fk_usuario_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."usuarios"
    ADD CONSTRAINT "fk_usuario_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: usuarios fk_usuario_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."usuarios"
    ADD CONSTRAINT "fk_usuario_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: vehiculos fk_vehiculo_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."vehiculos"
    ADD CONSTRAINT "fk_vehiculo_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: vehiculos fk_vehiculo_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."vehiculos"
    ADD CONSTRAINT "fk_vehiculo_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: zonas fk_zona_usuario_creacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."zonas"
    ADD CONSTRAINT "fk_zona_usuario_creacion" FOREIGN KEY ("usuario_creacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: zonas fk_zona_usuario_modificacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."zonas"
    ADD CONSTRAINT "fk_zona_usuario_modificacion" FOREIGN KEY ("usuario_modificacion") REFERENCES "public"."usuarios"("id_usuario") ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: series_documentos series_documentos_id_empresa_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."series_documentos"
    ADD CONSTRAINT "series_documentos_id_empresa_fkey" FOREIGN KEY ("id_empresa") REFERENCES "public"."api_empresas"("id_empresa");


--
-- PostgreSQL database dump complete
--



-- Marca técnica vacía. La fila se incorpora únicamente al cargar los datos de prueba.
CREATE TABLE public.dorna_demo_metadata (
    id integer NOT NULL,
    signature character varying(129) NOT NULL,
    CONSTRAINT dorna_demo_metadata_pkey PRIMARY KEY (id)
);

--
-- PostgreSQL database dump
--


-- Dumped from database version 18.6
-- Dumped by pg_dump version 18.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: roles; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.roles (id_rol, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (1, 'Administrador', 'Acceso completo al sistema', true, '2026-10-29 12:04:56.38478', '2026-10-29 12:04:56.38478', NULL, NULL);
INSERT INTO public.roles (id_rol, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (2, 'Gerencia', 'Acceso a toda la gestion', true, '2026-10-29 12:04:56.38478', '2026-10-29 12:04:56.38478', NULL, NULL);
INSERT INTO public.roles (id_rol, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (3, 'Administracion', 'Clientes, facturacion y cobros', true, '2026-10-29 12:04:56.38478', '2026-10-29 12:04:56.38478', NULL, NULL);
INSERT INTO public.roles (id_rol, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (4, 'Compras', 'Pedidos y proveedores', true, '2026-10-29 12:04:56.38478', '2026-10-29 12:04:56.38478', NULL, NULL);
INSERT INTO public.roles (id_rol, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (5, 'Almacen', 'Control de inventario', true, '2026-10-29 12:04:56.38478', '2026-10-29 12:04:56.38478', NULL, NULL);
INSERT INTO public.roles (id_rol, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (6, 'Operario', 'Gestion de obras y partes de trabajo', true, '2026-10-29 12:04:56.38478', '2026-10-29 12:04:56.38478', NULL, NULL);
INSERT INTO public.roles (id_rol, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (7, 'ADMIN', 'Administrador: todos los permisos de lectura y escritura', true, '2026-10-29 12:04:56.433183', '2026-10-29 12:04:56.433183', NULL, NULL);
INSERT INTO public.roles (id_rol, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (9, 'SOCIO', 'Socio: todos los permisos de lectura', true, '2026-10-29 12:04:56.433183', '2026-10-29 12:04:56.433183', NULL, NULL);
INSERT INTO public.roles (id_rol, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (10, 'LECTURA_EMPRESA', 'Lectura de los recursos de la empresa asignada', true, '2026-10-29 12:04:56.435619', '2026-10-29 12:04:56.435619', NULL, NULL);
INSERT INTO public.roles (id_rol, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (8, 'SOCIO_ADMIN', 'Perfil de socio con lectura; sin acceso global automatico', true, '2026-10-29 12:04:56.433183', '2026-10-29 12:04:56.439302', NULL, NULL);
INSERT INTO public.roles (id_rol, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (11, 'TRABAJADOR', 'Acceso limitado; las obras asignadas se habilitaran en su etapa', true, '2026-10-29 12:04:56.556963', '2026-10-29 12:04:56.556963', NULL, NULL);


--
-- Data for Name: api_roles_globales; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.api_roles_globales (id_rol) VALUES (7);
INSERT INTO public.api_roles_globales (id_rol) VALUES (9);


--
-- Data for Name: categorias_operarios; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.categorias_operarios (id_categoria, descripcion, coste_hora_base, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (1, 'Oficial 1', 24.50, true, '2026-10-29 12:04:56.428566', '2026-10-29 12:04:56.428566', NULL, NULL);
INSERT INTO public.categorias_operarios (id_categoria, descripcion, coste_hora_base, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (2, 'Oficial 2', 20.00, true, '2026-10-29 12:04:56.428566', '2026-10-29 12:04:56.428566', NULL, NULL);
INSERT INTO public.categorias_operarios (id_categoria, descripcion, coste_hora_base, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (3, 'Peon', 16.00, true, '2026-10-29 12:04:56.428566', '2026-10-29 12:04:56.428566', NULL, NULL);
INSERT INTO public.categorias_operarios (id_categoria, descripcion, coste_hora_base, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (4, 'Encargado', 30.00, true, '2026-10-29 12:04:56.428566', '2026-10-29 12:04:56.428566', NULL, NULL);


--
-- Data for Name: estados_factura; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.estados_factura (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (1, 'PENDIENTE', '#f59e0b', 1, true, '2026-10-29 12:04:56.39659', '2026-10-29 12:04:56.39659', NULL, NULL);
INSERT INTO public.estados_factura (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (2, 'ACEPTADO', '#16a34a', 2, true, '2026-10-29 12:04:56.39659', '2026-10-29 12:04:56.39659', NULL, NULL);
INSERT INTO public.estados_factura (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (3, 'RECHAZADO', '#dc2626', 3, true, '2026-10-29 12:04:56.39659', '2026-10-29 12:04:56.39659', NULL, NULL);
INSERT INTO public.estados_factura (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (4, 'RECIBIDO', '#2563eb', 4, true, '2026-10-29 12:04:56.39659', '2026-10-29 12:04:56.39659', NULL, NULL);
INSERT INTO public.estados_factura (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (5, 'FACTURADO', '#7c3aed', 5, true, '2026-10-29 12:04:56.39659', '2026-10-29 12:04:56.39659', NULL, NULL);
INSERT INTO public.estados_factura (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (6, 'PAGADA', '#16a34a', 6, true, '2026-10-29 12:04:56.39659', '2026-10-29 12:04:56.39659', NULL, NULL);
INSERT INTO public.estados_factura (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (7, 'PARCIAL', '#0891b2', 7, true, '2026-10-29 12:04:56.39659', '2026-10-29 12:04:56.39659', NULL, NULL);
INSERT INTO public.estados_factura (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (8, 'ANULADA', '#6b7280', 8, true, '2026-10-29 12:04:56.39659', '2026-10-29 12:04:56.39659', NULL, NULL);
INSERT INTO public.estados_factura (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (9, 'CANCELADA', '#dc2626', 9, true, '2026-10-29 12:04:56.39659', '2026-10-29 12:04:56.39659', NULL, NULL);
INSERT INTO public.estados_factura (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (10, 'EMITIDA_GESTION', NULL, 0, true, '2026-10-29 12:04:56.741806', '2026-10-29 12:04:56.741806', NULL, NULL);


--
-- Data for Name: estados_incidencia; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.estados_incidencia (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (1, 'ABIERTA', '#dc2626', 1, true, '2026-10-29 12:04:56.398675', '2026-10-29 12:04:56.398675', NULL, NULL);
INSERT INTO public.estados_incidencia (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (2, 'EN_PROCESO', '#f59e0b', 2, true, '2026-10-29 12:04:56.398675', '2026-10-29 12:04:56.398675', NULL, NULL);
INSERT INTO public.estados_incidencia (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (3, 'RESUELTA', '#16a34a', 3, true, '2026-10-29 12:04:56.398675', '2026-10-29 12:04:56.398675', NULL, NULL);
INSERT INTO public.estados_incidencia (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (4, 'CERRADA', '#6b7280', 4, true, '2026-10-29 12:04:56.398675', '2026-10-29 12:04:56.398675', NULL, NULL);


--
-- Data for Name: estados_obra; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.estados_obra (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (1, 'PRESUPUESTO', '#6b7280', 1, true, '2026-10-29 12:04:56.39505', '2026-10-29 12:04:56.39505', NULL, NULL);
INSERT INTO public.estados_obra (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (2, 'EN_CURSO', '#2563eb', 2, true, '2026-10-29 12:04:56.39505', '2026-10-29 12:04:56.39505', NULL, NULL);
INSERT INTO public.estados_obra (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (3, 'FINALIZADA', '#16a34a', 3, true, '2026-10-29 12:04:56.39505', '2026-10-29 12:04:56.39505', NULL, NULL);
INSERT INTO public.estados_obra (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (4, 'CANCELADA', '#dc2626', 4, true, '2026-10-29 12:04:56.39505', '2026-10-29 12:04:56.39505', NULL, NULL);


--
-- Data for Name: estados_tarea; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.estados_tarea (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (1, 'PENDIENTE', '#f59e0b', 1, true, '2026-10-29 12:04:56.397651', '2026-10-29 12:04:56.397651', NULL, NULL);
INSERT INTO public.estados_tarea (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (2, 'EN_CURSO', '#2563eb', 2, true, '2026-10-29 12:04:56.397651', '2026-10-29 12:04:56.397651', NULL, NULL);
INSERT INTO public.estados_tarea (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (3, 'FINALIZADA', '#16a34a', 3, true, '2026-10-29 12:04:56.397651', '2026-10-29 12:04:56.397651', NULL, NULL);
INSERT INTO public.estados_tarea (id_estado, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (4, 'CANCELADA', '#dc2626', 4, true, '2026-10-29 12:04:56.397651', '2026-10-29 12:04:56.397651', NULL, NULL);


--
-- Data for Name: formas_pago; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.formas_pago (id_forma_pago, descripcion, dias_vencimiento, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (1, 'Contado', 0, true, '2026-10-29 12:04:56.426258', '2026-10-29 12:04:56.426258', NULL, NULL);
INSERT INTO public.formas_pago (id_forma_pago, descripcion, dias_vencimiento, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (2, '30 dias', 30, true, '2026-10-29 12:04:56.426258', '2026-10-29 12:04:56.426258', NULL, NULL);
INSERT INTO public.formas_pago (id_forma_pago, descripcion, dias_vencimiento, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (3, '60 dias', 60, true, '2026-10-29 12:04:56.426258', '2026-10-29 12:04:56.426258', NULL, NULL);
INSERT INTO public.formas_pago (id_forma_pago, descripcion, dias_vencimiento, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (4, 'Transferencia', 0, true, '2026-10-29 12:04:56.426258', '2026-10-29 12:04:56.426258', NULL, NULL);
INSERT INTO public.formas_pago (id_forma_pago, descripcion, dias_vencimiento, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (5, 'Tarjeta', 0, true, '2026-10-29 12:04:56.426258', '2026-10-29 12:04:56.426258', NULL, NULL);


--
-- Data for Name: permisos; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (1, 'CLIENTES', 'Gestion de clientes', true, '2026-10-29 12:04:56.392135', '2026-10-29 12:04:56.392135', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (2, 'PROVEEDORES', 'Gestion de proveedores', true, '2026-10-29 12:04:56.392135', '2026-10-29 12:04:56.392135', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (3, 'OPERARIOS', 'Gestion de operarios', true, '2026-10-29 12:04:56.392135', '2026-10-29 12:04:56.392135', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (4, 'ARTICULOS', 'Gestion de articulos', true, '2026-10-29 12:04:56.392135', '2026-10-29 12:04:56.392135', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (5, 'ALMACEN', 'Gestion de almacenes', true, '2026-10-29 12:04:56.392135', '2026-10-29 12:04:56.392135', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (6, 'OBRAS', 'Gestion de obras', true, '2026-10-29 12:04:56.392135', '2026-10-29 12:04:56.392135', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (7, 'PRESUPUESTOS', 'Gestion de presupuestos', true, '2026-10-29 12:04:56.392135', '2026-10-29 12:04:56.392135', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (8, 'PEDIDOS', 'Gestion de pedidos', true, '2026-10-29 12:04:56.392135', '2026-10-29 12:04:56.392135', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (9, 'ALBARANES', 'Gestion de albaranes', true, '2026-10-29 12:04:56.392135', '2026-10-29 12:04:56.392135', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (10, 'FACTURAS', 'Gestion de facturacion', true, '2026-10-29 12:04:56.392135', '2026-10-29 12:04:56.392135', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (11, 'COBROS', 'Gestion de cobros', true, '2026-10-29 12:04:56.392135', '2026-10-29 12:04:56.392135', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (12, 'PAGOS', 'Gestion de pagos', true, '2026-10-29 12:04:56.392135', '2026-10-29 12:04:56.392135', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (13, 'VEHICULOS', 'Gestion de vehiculos', true, '2026-10-29 12:04:56.392135', '2026-10-29 12:04:56.392135', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (14, 'USUARIOS', 'Administracion de usuarios', true, '2026-10-29 12:04:56.392135', '2026-10-29 12:04:56.392135', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (15, 'CONFIGURACION', 'Configuracion del sistema', true, '2026-10-29 12:04:56.392135', '2026-10-29 12:04:56.392135', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (16, 'ESTADISTICAS', 'Informes y estadisticas', true, '2026-10-29 12:04:56.392135', '2026-10-29 12:04:56.392135', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (17, 'EMPRESA', 'Consulta de la ficha de empresa propia de la API', true, '2026-10-29 12:04:56.431211', '2026-10-29 12:04:56.431211', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (21, 'DOCUMENTOS', 'Consulta y descarga de documentos de obra autorizados', true, '2026-10-29 12:04:56.431585', '2026-10-29 12:04:56.431585', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (23, 'PARTES_MANO_OBRA', 'Consulta historica de horas registradas, sin costes ni observaciones', true, '2026-10-29 12:04:56.431764', '2026-10-29 12:04:56.431764', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (24, 'FACTURAS_CLIENTE', 'Lectura de facturas y lineas de cliente', true, '2026-10-29 12:04:56.431986', '2026-10-29 12:04:56.431986', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (25, 'FACTURAS_PROVEEDOR', 'Lectura de facturas y lineas de proveedor', true, '2026-10-29 12:04:56.431986', '2026-10-29 12:04:56.431986', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (26, 'INFORMES', 'Consulta de agregaciones DEMO, ademas de los permisos de cada fuente', true, '2026-10-29 12:04:56.432174', '2026-10-29 12:04:56.432174', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (28, 'ALMACENES', 'Consulta de almacenes', true, '2026-10-29 12:04:56.432327', '2026-10-29 12:04:56.432327', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (29, 'STOCK', 'Consulta de movimientos y saldo por almacen DEMO', true, '2026-10-29 12:04:56.432327', '2026-10-29 12:04:56.432327', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (32, 'ALBARANES_CLIENTE', 'Lectura de albaranes de cliente y lineas historicas', true, '2026-10-29 12:04:56.432625', '2026-10-29 12:04:56.432625', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (33, 'ALBARANES_PROVEEDOR', 'Lectura de albaranes de proveedor y lineas historicas', true, '2026-10-29 12:04:56.432625', '2026-10-29 12:04:56.432625', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (34, 'INCIDENCIAS', 'Consulta de incidencias operativas y su descripcion de detalle', true, '2026-10-29 12:04:56.4328', '2026-10-29 12:04:56.4328', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (35, 'EVENTOS', 'Consulta del calendario operativo, excluidos eventos personales y vacaciones', true, '2026-10-29 12:04:56.432998', '2026-10-29 12:04:56.432998', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (36, 'TAREAS', 'Consulta de tareas operativas y su detalle', true, '2026-10-29 12:04:56.432998', '2026-10-29 12:04:56.432998', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (37, 'TRABAJO_PROPIO', 'Consulta operativa del empleado vinculado y sus obras asignadas', true, '2026-10-29 12:04:56.619584', '2026-10-29 12:04:56.619584', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (38, 'OBSERVACIONES', 'Consulta y gestion de observaciones operativas de obra', true, '2026-10-29 12:04:56.667776', '2026-10-29 12:04:56.667776', NULL, NULL);
INSERT INTO public.permisos (id_permiso, nombre, descripcion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (39, 'AUDITORIA', 'Consulta reservada de auditoria transversal', true, '2026-10-29 12:04:56.887019', '2026-10-29 12:04:56.887019', NULL, NULL);


--
-- Data for Name: prioridades; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.prioridades (id_prioridad, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (1, 'BAJA', '#22c55e', 1, true, '2026-10-29 12:04:56.425081', '2026-10-29 12:04:56.425081', NULL, NULL);
INSERT INTO public.prioridades (id_prioridad, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (2, 'MEDIA', '#f59e0b', 2, true, '2026-10-29 12:04:56.425081', '2026-10-29 12:04:56.425081', NULL, NULL);
INSERT INTO public.prioridades (id_prioridad, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (3, 'ALTA', '#f97316', 3, true, '2026-10-29 12:04:56.425081', '2026-10-29 12:04:56.425081', NULL, NULL);
INSERT INTO public.prioridades (id_prioridad, descripcion, color, orden_visualizacion, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (4, 'URGENTE', '#dc2626', 4, true, '2026-10-29 12:04:56.425081', '2026-10-29 12:04:56.425081', NULL, NULL);


--
-- Data for Name: rol_permiso; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (1, 1, 1, 'ADMIN', '2026-10-29 12:04:56.393166', '2026-10-29 12:04:56.393166', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (2, 1, 2, 'ADMIN', '2026-10-29 12:04:56.393166', '2026-10-29 12:04:56.393166', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (3, 1, 3, 'ADMIN', '2026-10-29 12:04:56.393166', '2026-10-29 12:04:56.393166', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (4, 1, 4, 'ADMIN', '2026-10-29 12:04:56.393166', '2026-10-29 12:04:56.393166', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (5, 1, 5, 'ADMIN', '2026-10-29 12:04:56.393166', '2026-10-29 12:04:56.393166', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (6, 1, 6, 'ADMIN', '2026-10-29 12:04:56.393166', '2026-10-29 12:04:56.393166', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (7, 1, 7, 'ADMIN', '2026-10-29 12:04:56.393166', '2026-10-29 12:04:56.393166', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (8, 1, 8, 'ADMIN', '2026-10-29 12:04:56.393166', '2026-10-29 12:04:56.393166', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (9, 1, 9, 'ADMIN', '2026-10-29 12:04:56.393166', '2026-10-29 12:04:56.393166', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (10, 1, 10, 'ADMIN', '2026-10-29 12:04:56.393166', '2026-10-29 12:04:56.393166', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (11, 1, 11, 'ADMIN', '2026-10-29 12:04:56.393166', '2026-10-29 12:04:56.393166', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (12, 1, 12, 'ADMIN', '2026-10-29 12:04:56.393166', '2026-10-29 12:04:56.393166', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (13, 1, 13, 'ADMIN', '2026-10-29 12:04:56.393166', '2026-10-29 12:04:56.393166', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (14, 1, 14, 'ADMIN', '2026-10-29 12:04:56.393166', '2026-10-29 12:04:56.393166', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (15, 1, 15, 'ADMIN', '2026-10-29 12:04:56.393166', '2026-10-29 12:04:56.393166', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (16, 1, 16, 'ADMIN', '2026-10-29 12:04:56.393166', '2026-10-29 12:04:56.393166', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (17, 7, 1, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (18, 8, 1, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (19, 9, 1, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (107, 10, 1, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (20, 7, 2, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (21, 8, 2, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (22, 9, 2, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (111, 10, 2, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (23, 7, 3, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (24, 8, 3, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (25, 9, 3, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (115, 10, 3, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (26, 7, 4, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (27, 8, 4, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (28, 9, 4, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (119, 10, 4, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (29, 7, 5, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (30, 8, 5, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (31, 9, 5, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (123, 10, 5, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (32, 7, 6, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (33, 8, 6, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (34, 9, 6, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (127, 10, 6, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (35, 7, 7, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (36, 8, 7, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (37, 9, 7, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (131, 10, 7, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (38, 7, 8, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (39, 8, 8, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (40, 9, 8, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (135, 10, 8, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (41, 7, 9, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (42, 8, 9, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (43, 9, 9, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (139, 10, 9, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (44, 7, 10, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (45, 8, 10, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (46, 9, 10, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (143, 10, 10, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (47, 7, 11, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (48, 8, 11, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (49, 9, 11, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (147, 10, 11, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (50, 7, 12, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (51, 8, 12, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (52, 9, 12, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (151, 10, 12, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (53, 7, 13, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (54, 8, 13, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (55, 9, 13, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (155, 10, 13, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (56, 7, 14, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (57, 8, 14, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (58, 9, 14, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (159, 10, 14, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (59, 7, 15, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (60, 8, 15, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (61, 9, 15, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (163, 10, 15, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (62, 7, 16, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (63, 8, 16, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (64, 9, 16, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (167, 10, 16, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (65, 7, 17, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (66, 8, 17, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (67, 9, 17, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (171, 10, 17, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (68, 7, 21, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (69, 8, 21, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (70, 9, 21, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (175, 10, 21, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (71, 7, 23, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (72, 8, 23, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (73, 9, 23, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (179, 10, 23, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (74, 7, 24, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (75, 8, 24, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (76, 9, 24, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (183, 10, 24, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (77, 7, 25, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (78, 8, 25, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (79, 9, 25, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (187, 10, 25, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (80, 7, 26, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (81, 8, 26, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (82, 9, 26, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (191, 10, 26, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (83, 7, 28, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (84, 8, 28, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (85, 9, 28, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (195, 10, 28, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (86, 7, 29, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (87, 8, 29, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (88, 9, 29, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (199, 10, 29, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (89, 7, 32, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (90, 8, 32, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (91, 9, 32, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (203, 10, 32, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (92, 7, 33, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (93, 8, 33, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (94, 9, 33, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (207, 10, 33, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (95, 7, 34, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (96, 8, 34, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (97, 9, 34, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (211, 10, 34, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (98, 7, 35, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (99, 8, 35, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (100, 9, 35, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (215, 10, 35, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (101, 7, 36, 'ADMIN', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (102, 8, 36, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (103, 9, 36, 'LECTURA', '2026-10-29 12:04:56.434212', '2026-10-29 12:04:56.434212', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (219, 10, 36, 'LECTURA', '2026-10-29 12:04:56.436424', '2026-10-29 12:04:56.436424', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (220, 11, 17, 'LECTURA', '2026-10-29 12:04:56.563642', '2026-10-29 12:04:56.563642', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (221, 7, 37, 'ADMIN', '2026-10-29 12:04:56.62532', '2026-10-29 12:04:56.62532', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (222, 9, 37, 'LECTURA', '2026-10-29 12:04:56.62532', '2026-10-29 12:04:56.62532', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (223, 10, 37, 'LECTURA', '2026-10-29 12:04:56.62532', '2026-10-29 12:04:56.62532', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (224, 8, 37, 'LECTURA', '2026-10-29 12:04:56.62532', '2026-10-29 12:04:56.62532', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (225, 11, 37, 'LECTURA', '2026-10-29 12:04:56.62532', '2026-10-29 12:04:56.62532', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (226, 7, 38, 'ADMIN', '2026-10-29 12:04:56.673681', '2026-10-29 12:04:56.673681', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (227, 9, 38, 'LECTURA', '2026-10-29 12:04:56.673681', '2026-10-29 12:04:56.673681', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (228, 10, 38, 'LECTURA', '2026-10-29 12:04:56.673681', '2026-10-29 12:04:56.673681', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (229, 8, 38, 'LECTURA', '2026-10-29 12:04:56.673681', '2026-10-29 12:04:56.673681', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (230, 11, 38, 'ESCRITURA', '2026-10-29 12:04:56.673681', '2026-10-29 12:04:56.673681', NULL, NULL);
INSERT INTO public.rol_permiso (id_rol_permiso, id_rol, id_permiso, nivel_acceso, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (231, 7, 39, 'ADMIN', '2026-10-29 12:04:56.892779', '2026-10-29 12:04:56.892779', NULL, NULL);


--
-- Data for Name: tipos_iva; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.tipos_iva (id_iva, descripcion, porcentaje, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (1, 'General', 21.00, true, '2026-10-29 12:04:56.427172', '2026-10-29 12:04:56.427172', NULL, NULL);
INSERT INTO public.tipos_iva (id_iva, descripcion, porcentaje, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (2, 'Reducido', 10.00, true, '2026-10-29 12:04:56.427172', '2026-10-29 12:04:56.427172', NULL, NULL);
INSERT INTO public.tipos_iva (id_iva, descripcion, porcentaje, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (3, 'Superreducido', 4.00, true, '2026-10-29 12:04:56.427172', '2026-10-29 12:04:56.427172', NULL, NULL);
INSERT INTO public.tipos_iva (id_iva, descripcion, porcentaje, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (4, 'Exento', 0.00, true, '2026-10-29 12:04:56.427172', '2026-10-29 12:04:56.427172', NULL, NULL);


--
-- Data for Name: unidades_medida; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.unidades_medida (id_unidad, descripcion, abreviatura, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (1, 'Unidad', 'ud', true, '2026-10-29 12:04:56.430154', '2026-10-29 12:04:56.430154', NULL, NULL);
INSERT INTO public.unidades_medida (id_unidad, descripcion, abreviatura, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (2, 'Metro', 'm', true, '2026-10-29 12:04:56.430154', '2026-10-29 12:04:56.430154', NULL, NULL);
INSERT INTO public.unidades_medida (id_unidad, descripcion, abreviatura, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (3, 'Metro cuadrado', 'm2', true, '2026-10-29 12:04:56.430154', '2026-10-29 12:04:56.430154', NULL, NULL);
INSERT INTO public.unidades_medida (id_unidad, descripcion, abreviatura, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (4, 'Metro cubico', 'm3', true, '2026-10-29 12:04:56.430154', '2026-10-29 12:04:56.430154', NULL, NULL);
INSERT INTO public.unidades_medida (id_unidad, descripcion, abreviatura, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (5, 'Kilogramo', 'kg', true, '2026-10-29 12:04:56.430154', '2026-10-29 12:04:56.430154', NULL, NULL);
INSERT INTO public.unidades_medida (id_unidad, descripcion, abreviatura, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion) VALUES (6, 'Litro', 'l', true, '2026-10-29 12:04:56.430154', '2026-10-29 12:04:56.430154', NULL, NULL);


--
-- Name: categorias_operarios_id_categoria_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.categorias_operarios_id_categoria_seq', 4, true);


--
-- Name: estados_factura_id_estado_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.estados_factura_id_estado_seq', 10, true);


--
-- Name: estados_incidencia_id_estado_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.estados_incidencia_id_estado_seq', 4, true);


--
-- Name: estados_obra_id_estado_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.estados_obra_id_estado_seq', 4, true);


--
-- Name: estados_tarea_id_estado_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.estados_tarea_id_estado_seq', 4, true);


--
-- Name: formas_pago_id_forma_pago_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.formas_pago_id_forma_pago_seq', 5, true);


--
-- Name: permisos_id_permiso_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.permisos_id_permiso_seq', 39, true);


--
-- Name: prioridades_id_prioridad_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.prioridades_id_prioridad_seq', 4, true);


--
-- Name: rol_permiso_id_rol_permiso_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.rol_permiso_id_rol_permiso_seq', 231, true);


--
-- Name: roles_id_rol_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.roles_id_rol_seq', 11, true);


--
-- Name: tipos_iva_id_iva_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.tipos_iva_id_iva_seq', 4, true);


--
-- Name: unidades_medida_id_unidad_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.unidades_medida_id_unidad_seq', 6, true);


--
-- PostgreSQL database dump complete
--



COMMIT;
